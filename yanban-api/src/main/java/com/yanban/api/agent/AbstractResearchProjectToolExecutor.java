package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectFileResponse;
import com.yanban.api.project.ProjectManifestResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ProjectVersionRef;
import com.yanban.core.research.ResearchBudgetUsage;
import com.yanban.core.research.ResearchCallKey;
import com.yanban.core.research.ResearchContractException;
import com.yanban.core.research.ResearchToolContract;
import com.yanban.core.research.ResearchToolContracts;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolDefinition;
import com.yanban.core.tool.ToolDescriptor;
import com.yanban.core.tool.ToolErrorCode;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolExecutor;
import com.yanban.core.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fail-closed executor boundary for the first read-only research tools.  Model arguments never
 * carry a user, project, capability, or host path: those values are re-attested here and every
 * file read is delegated back to {@link ProjectService}.
 */
abstract class AbstractResearchProjectToolExecutor implements ToolExecutor {

    protected static final String RESEARCH_PROJECT_READ = "research:project-read";
    protected final ProjectService projects;
    protected final ObjectMapper objectMapper;
    private final ResearchToolContract contract;

    AbstractResearchProjectToolExecutor(String toolName, ProjectService projects, ObjectMapper objectMapper) {
        this.contract = ResearchToolContracts.byName(toolName);
        this.projects = projects;
        this.objectMapper = objectMapper;
    }

    @Override public final ToolDefinition definition() { return contract.definition(); }

    protected final ResearchToolContract contract() { return contract; }

    @Override public ToolDescriptor descriptor() {
        // The frozen contract is intentionally not itself model-visible.  Registration supplies
        // the governed runtime descriptor that is still intersected with resolved policy.
        return new ToolDescriptor(definition().name(), "v1", "scientific-project-read",
                List.of(ToolDescriptor.CapabilityProfile.PROJECT), List.of(RESEARCH_PROJECT_READ),
                List.of(ToolDescriptor.ResourceScope.PROJECT), ToolDescriptor.SideEffectType.READ_ONLY,
                ToolDescriptor.ConfirmationPolicy.NEVER, ToolDescriptor.AsyncMode.SYNC,
                ToolDescriptor.IdempotencyPolicy.NONE, ToolDescriptor.RepeatPolicy.DENY_SAME_INPUT, true);
    }

    @Override public final ToolResult execute(ToolCall call) {
        if (call == null || !definition().name().equals(call.name())) {
            return failure(call, ToolErrorCode.VALIDATION_ERROR, "research tool call does not match its definition");
        }
        Long userId = ToolExecutionContext.getCurrentUserId();
        Long projectId = ToolExecutionContext.getCurrentProjectId();
        if (userId == null || projectId == null || !ToolExecutionContext.isToolAllowed(definition().name())) {
            return failure(call, ToolErrorCode.PERMISSION_DENIED, "research tool requires an attested Project READ_ONLY context and resolved allow-list");
        }
        try {
            // manifest performs the ownership and READ_ONLY check at the executor boundary.
            ProjectManifestResponse manifest = projects.manifest(userId, projectId);
            ProjectVersionRef version = new ProjectVersionRef(manifest.version());
            ResearchCallKey key = contract.callKey(version, call.arguments());
            ResearchContext context = new ResearchContext(userId, projectId, version, key, manifest.files());
            ResearchToolOutcome outcome = analyze(context, call.arguments());
            contract.validateOutcome(outcome);
            return new ToolResult(call.id(), definition().name(), true, objectMapper.valueToTree(outcome), null, null,
                    false, outcome.evidenceRefs().stream().map(Object::toString).toList(), List.of(), List.of(), version.value());
        } catch (ResearchContractException exception) {
            return failure(call, errorFor(exception.errorCode()), exception.getMessage());
        } catch (ResponseStatusException exception) {
            return failure(call, ToolErrorCode.PERMISSION_DENIED, "authorized Project content is unavailable");
        } catch (RuntimeException exception) {
            return failure(call, ToolErrorCode.INTERNAL_ERROR, "research tool failed closed");
        }
    }

    protected abstract ResearchToolOutcome analyze(ResearchContext context, JsonNode arguments);

    protected final Map<ProjectRelativePath, ProjectFileEntry> requestedFiles(ResearchContext context, JsonNode arguments,
                                                                                boolean defaultToManifest) {
        Map<ProjectRelativePath, ProjectFileEntry> available = new LinkedHashMap<>();
        for (ProjectFileEntry file : context.manifestFiles()) available.put(ProjectRelativePath.of(file.path()), file);
        List<ProjectRelativePath> requested = new ArrayList<>();
        JsonNode paths = arguments.path("relativePaths");
        if (paths.isArray()) paths.forEach(value -> requested.add(ProjectRelativePath.of(value.textValue())));
        else if (defaultToManifest) requested.addAll(available.keySet());
        Map<ProjectRelativePath, ProjectFileEntry> result = new LinkedHashMap<>();
        for (ProjectRelativePath path : requested) {
            ProjectFileEntry entry = available.get(path);
            if (entry != null) result.put(path, entry);
        }
        if (!requested.isEmpty() && result.isEmpty()) {
            throw new ResearchContractException(ResearchToolErrorCode.INVALID_ARGUMENT,
                    "no requested Project-relative path is present in the attested manifest");
        }
        return result;
    }

    protected final ProjectFileResponse read(ResearchContext context, ProjectRelativePath path) {
        // ProjectService is the only path-to-content bridge. No executor resolves a filesystem path.
        ProjectFileEntry expected = context.manifestFiles().stream()
                .filter(file -> file.path().equals(path.value())).findFirst()
                .orElseThrow(() -> new ResearchContractException(ResearchToolErrorCode.INDEX_STALE,
                        "INDEX_STALE: requested file is absent from the attested manifest"));
        ProjectFileResponse actual;
        try {
            actual = projects.readFile(context.userId(), context.projectId(), path.value());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResearchContractException(ResearchToolErrorCode.INDEX_STALE,
                        "INDEX_STALE: manifest-listed Project file is no longer readable");
            }
            throw exception;
        }
        if (!path.value().equals(actual.path()) || !expected.sha256().equals(actual.sha256())) {
            throw new ResearchContractException(ResearchToolErrorCode.INDEX_STALE,
                    "INDEX_STALE: Project content changed after manifest attestation");
        }
        return actual;
    }

    protected final long utf8Bytes(ProjectFileResponse file) {
        return file.content().getBytes(StandardCharsets.UTF_8).length;
    }

    protected final ResearchBudgetUsage usage(int inputPaths, int outputItems, int evidenceRefs, long bytesInspected) {
        return new ResearchBudgetUsage(inputPaths, outputItems, evidenceRefs, bytesInspected);
    }

    private ToolResult failure(ToolCall call, ToolErrorCode error, String message) {
        return ToolResult.failure(call == null ? null : call.id(), definition().name(), error, message);
    }

    private ToolErrorCode errorFor(ResearchToolErrorCode error) {
        return switch (error) {
            case INVALID_ARGUMENT, PATH_OUTSIDE_PROJECT, UNSUPPORTED_FILE_TYPE -> ToolErrorCode.VALIDATION_ERROR;
            case PROJECT_SCOPE_UNAVAILABLE -> ToolErrorCode.PERMISSION_DENIED;
            case BUDGET_EXCEEDED -> ToolErrorCode.RATE_LIMITED;
            case INDEX_STALE -> ToolErrorCode.CONFLICT;
            case TRANSIENT_PROJECT_IO -> ToolErrorCode.TRANSIENT_EXTERNAL_ERROR;
            default -> ToolErrorCode.INTERNAL_ERROR;
        };
    }

    protected record ResearchContext(Long userId, Long projectId, ProjectVersionRef projectVersion,
                                     ResearchCallKey callKey, List<ProjectFileEntry> manifestFiles) {
        protected ResearchContext { manifestFiles = List.copyOf(manifestFiles); }
    }
}
