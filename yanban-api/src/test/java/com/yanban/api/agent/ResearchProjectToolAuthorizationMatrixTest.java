package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.api.project.LocalServerProjectRootProvider;
import com.yanban.api.project.Project;
import com.yanban.api.project.ProjectAccessMode;
import com.yanban.api.project.ProjectRepository;
import com.yanban.api.project.ProjectService;
import com.yanban.api.project.ProjectStorageProperties;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolErrorCode;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** Batch-A matrix: every row invokes every real research executor over a real ProjectService. */
class ResearchProjectToolAuthorizationMatrixTest {
    private static final long USER = 7L;
    private static final long PROJECT = 42L;
    @TempDir Path tempDir;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach void clear() { ToolExecutionContext.clear(); }

    @ParameterizedTest(name = "{0}:{1}")
    @MethodSource("rejectionCases")
    void rejectsAuthorizationAndUnsafePathsWithoutReadingFiles(String toolName, String scenario,
                                                               BiFunction<ProjectService, ObjectMapper, ToolExecutor> factory,
                                                               String path, ToolErrorCode expected) throws Exception {
        Fixture fixture = fixture(); ProjectService spy = Mockito.spy(fixture.service());
        ToolExecutor executor = factory.apply(spy, json); ObjectNode arguments = arguments(toolName, path);
        configureContext(scenario);
        String normalizedScenario = scenario.substring(0, scenario.indexOf(':'));
        if ("wrong_owner".equals(normalizedScenario)) when(fixture.repository().findByIdAndUserId(PROJECT, USER)).thenReturn(Optional.empty());
        if ("read_write".equals(normalizedScenario)) ReflectionTestUtils.setField(fixture.project(), "accessMode", ProjectAccessMode.READ_WRITE);

        var result = executor.execute(new ToolCall("call-" + toolName, toolName, arguments));

        assertThat(result.success()).isFalse(); assertThat(result.errorCode()).isEqualTo(expected);
        assertThat(result.output()).isNull(); assertThat(result.evidenceRefs()).isEmpty();
        verify(spy, never()).readFile(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest(name = "readable:{0}")
    @MethodSource("executors")
    void acceptsChineseAndSpaceRelativePathForEveryExecutor(String toolName,
                                                            BiFunction<ProjectService, ObjectMapper, ToolExecutor> factory) throws Exception {
        Fixture fixture = fixture(); ProjectService spy = Mockito.spy(fixture.service()); ToolExecutionContext.setCurrentUserId(USER); ToolExecutionContext.setCurrentProjectId(PROJECT);
        ToolExecutionContext.setResolvedAllowedTools(Set.of(toolName));
        var result = factory.apply(spy, json).execute(new ToolCall("unicode-" + toolName, toolName,
                normalArguments(toolName)));
        assertThat(result.success()).isTrue(); assertThat(result.output().path("status").asText()).isEqualTo("COMPLETE");
        assertThat(result.output().path("items").size()).isPositive();
        verify(spy).readFile(USER, PROJECT, normalPath(toolName));
    }

    static Stream<Arguments> executors() {
        return Stream.of(
                Arguments.of("project_latex_outline", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectLatexOutlineToolExecutor::new),
                Arguments.of("project_bibtex_audit", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectBibtexAuditToolExecutor::new),
                Arguments.of("project_code_symbols", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCodeSymbolsToolExecutor::new),
                Arguments.of("project_experiment_summary", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectExperimentSummaryToolExecutor::new),
                Arguments.of("project_cross_material_search", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCrossMaterialSearchToolExecutor::new));
    }

    static Stream<Arguments> rejectionCases() {
        return executors().flatMap(executor -> Stream.of(
                row(executor, "missing_user", "safe"), row(executor, "missing_project", "safe"),
                row(executor, "empty_allowlist", "safe"), row(executor, "wrong_allowlist", "safe"),
                row(executor, "wrong_owner", "safe"), row(executor, "read_write", "safe"),
                row(executor, "valid", "C:/secret"), row(executor, "valid", "\\\\server\\share\\secret"),
                row(executor, "valid", "../secret"), row(executor, "valid", "bad\u0001path"), row(executor, "valid", "missing.file")));
    }

    private static Arguments row(Arguments executor, String scenario, String path) {
        Object[] values = executor.get(); String tool = (String) values[0];
        ToolErrorCode error = switch (scenario) {
            case "missing_user", "missing_project", "empty_allowlist", "wrong_allowlist", "wrong_owner", "read_write" -> ToolErrorCode.PERMISSION_DENIED;
            default -> ToolErrorCode.VALIDATION_ERROR;
        };
        return Arguments.of(tool, scenario + ":" + path, values[1], path, error);
    }

    private void configureContext(String labelledScenario) {
        String scenario = labelledScenario.substring(0, labelledScenario.indexOf(':'));
        if (!"missing_user".equals(scenario)) ToolExecutionContext.setCurrentUserId(USER);
        if (!"missing_project".equals(scenario)) ToolExecutionContext.setCurrentProjectId(PROJECT);
        if (!Set.of("missing_user", "missing_project", "empty_allowlist").contains(scenario))
            ToolExecutionContext.setResolvedAllowedTools("wrong_allowlist".equals(scenario) ? Set.of("other") : Set.of(
                    "project_latex_outline", "project_bibtex_audit", "project_code_symbols", "project_experiment_summary", "project_cross_material_search"));
        else if ("empty_allowlist".equals(scenario)) ToolExecutionContext.setResolvedAllowedTools(Set.of());
    }

    private ObjectNode arguments(String tool, String path) {
        ObjectNode value = json.createObjectNode();
        if ("project_cross_material_search".equals(tool)) value.put("query", "needle");
        value.putArray("relativePaths").add("safe".equals(path) ? normalPath(tool) : path);
        return value;
    }

    private ObjectNode normalArguments(String tool) {
        ObjectNode result = arguments(tool, normalPath(tool));
        if ("project_cross_material_search".equals(tool)) result.withArray("relativePaths").add("资料 空间/第二 文件.tex");
        return result;
    }

    private String normalPath(String tool) { return "资料 空间/研究 文件." + extension(tool); }
    private String extension(String tool) { return switch (tool) {
        case "project_latex_outline", "project_cross_material_search" -> "tex";
        case "project_bibtex_audit" -> "bib"; case "project_code_symbols" -> "java"; default -> "json"; }; }

    private Fixture fixture() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root")); Path materials = Files.createDirectories(root.resolve("资料 空间"));
        Files.writeString(materials.resolve("研究 文件.tex"), "\\section{needle}\n");
        Files.writeString(materials.resolve("研究 文件.bib"), "@article{k,\n title = {t},\n year = {2024}\n}\n");
        Files.writeString(materials.resolve("研究 文件.java"), "class Needle {}\n");
        Files.writeString(materials.resolve("研究 文件.json"), "{\"needle\": 1}\n");
        Files.writeString(materials.resolve("第二 文件.tex"), "needle\n");
        ProjectStorageProperties properties = new ProjectStorageProperties(); properties.setLocalServerRoot(root.toString());
        ProjectRepository repository = Mockito.mock(ProjectRepository.class);
        Project project = new Project(USER, "Study", ".", root.toRealPath().toString(), "[\"**\"]", "[]");
        when(repository.findByIdAndUserId(PROJECT, USER)).thenReturn(Optional.of(project));
        return new Fixture(new ProjectService(repository, List.of(new LocalServerProjectRootProvider(properties)), properties, json), repository, project);
    }

    private record Fixture(ProjectService service, ProjectRepository repository, Project project) { }
}
