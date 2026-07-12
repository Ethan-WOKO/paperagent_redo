package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.api.project.LocalServerProjectRootProvider;
import com.yanban.api.project.Project;
import com.yanban.api.project.ProjectManifestResponse;
import com.yanban.api.project.ProjectRepository;
import com.yanban.api.project.ProjectService;
import com.yanban.api.project.ProjectStorageProperties;
import com.yanban.core.research.ResearchToolContracts;
import com.yanban.core.tool.ToolCall;
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

/** Batch-B1 real ProjectService evidence and frozen byte-budget matrix. */
class ResearchProjectToolEvidenceAndBudgetTest {
    private static final long USER = 7L, PROJECT = 42L;
    @TempDir Path tempDir;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach void clear() { ToolExecutionContext.clear(); }

    @ParameterizedTest(name = "evidence:{0}")
    @MethodSource("executors")
    void producesExactAttestedEvidenceForEveryTool(String tool, BiFunction<ProjectService,ObjectMapper,ToolExecutor> factory,
                                                   String path, int line, String parser) throws Exception {
        Fixture fixture = fixture(false, tool); ProjectManifestResponse manifest = fixture.service().manifest(USER, PROJECT);
        ToolExecutionContext.setCurrentUserId(USER); ToolExecutionContext.setCurrentProjectId(PROJECT); ToolExecutionContext.setResolvedAllowedTools(Set.of(tool));
        var result = factory.apply(fixture.service(), json).execute(new ToolCall("e-" + tool, tool, arguments(tool, path)));

        assertThat(result.success()).isTrue(); assertThat(result.output().path("status").asText()).isEqualTo("COMPLETE");
        assertThat(result.output().path("items").size()).isPositive(); assertThat(result.version()).isEqualTo(manifest.version());
        var entry = manifest.files().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow();
        var envelope = result.output().path("evidenceRefs"); assertThat(envelope.size()).isPositive();
        envelope.forEach(evidence -> {
            assertThat(evidence.path("projectVersion").asText()).isEqualTo(manifest.version());
            assertThat(evidence.path("relativePath").asText()).isIn(path, "资料 空间/第二 文件.tex");
            var evidenceEntry = manifest.files().stream().filter(file -> file.path().equals(evidence.path("relativePath").asText()))
                    .findFirst().orElseThrow();
            assertThat(evidence.path("fileHash").asText()).isEqualTo(evidenceEntry.sha256());
            assertThat(evidence.path("parserVersion").asText()).isEqualTo(parser);
            assertThat(evidence.path("trustLabel").asText()).isEqualTo("SERVER_ATTESTED_METADATA");
            assertThat(evidence.path("range").path("startLine").asInt()).isGreaterThanOrEqualTo(1);
        });
        assertThat(envelope.toString()).doesNotContain(tempDir.toString(), "projectId", "userId", "canonicalRootPath");
        result.output().path("items").forEach(item -> {
            assertThat(item.path("content").path("trustLabel").asText()).isEqualTo("UNTRUSTED_PROJECT_CONTENT");
            assertThat(envelope).anySatisfy(value -> assertThat(value).isEqualTo(item.path("content").path("evidence")));
        });
        if (tool.equals("project_cross_material_search")) {
            var linked = result.output().path("items").get(0).path("linkedEvidence");
            assertThat(linked.size()).isGreaterThanOrEqualTo(2);
            assertThat(linked).allSatisfy(link -> assertThat(envelope).anySatisfy(value -> assertThat(value).isEqualTo(link)));
            assertThat(linked).extracting(link -> link.path("relativePath").asText()).doesNotHaveDuplicates();
        }
        assertThat(envelope.get(0).path("range").path("startLine").asInt()).isEqualTo(line);
    }

    @ParameterizedTest(name = "byte-budget:{0}")
    @MethodSource("budgetExecutors")
    void rejectsFirstInputBeyondFrozenByteBudget(String tool, BiFunction<ProjectService,ObjectMapper,ToolExecutor> factory,
                                                 String path) throws Exception {
        Fixture fixture = fixture(true, tool); ToolExecutionContext.setCurrentUserId(USER); ToolExecutionContext.setCurrentProjectId(PROJECT);
        ToolExecutionContext.setResolvedAllowedTools(Set.of(tool));
        var result = factory.apply(fixture.service(), json).execute(new ToolCall("b-" + tool, tool, arguments(tool, path)));
        assertThat(result.success()).isFalse(); assertThat(result.errorCode().name()).isEqualTo("RATE_LIMITED");
        assertThat(result.output()).isNull(); assertThat(result.evidenceRefs()).isEmpty();
    }

    static Stream<Arguments> executors() {
        return Stream.of(
                Arguments.of("project_latex_outline", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectLatexOutlineToolExecutor::new, "资料 空间/论文 主.tex", 2, "latex-outline@1"),
                Arguments.of("project_bibtex_audit", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectBibtexAuditToolExecutor::new, "资料 空间/文献 库.bib", 1, "bibtex-audit@1"),
                Arguments.of("project_code_symbols", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCodeSymbolsToolExecutor::new, "资料 空间/模型 代码.java", 2, "code-symbols@1"),
                Arguments.of("project_experiment_summary", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectExperimentSummaryToolExecutor::new, "资料 空间/指标 文件.json", 1, "experiment-summary@1"),
                Arguments.of("project_cross_material_search", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCrossMaterialSearchToolExecutor::new, "资料 空间/论文 主.tex", 2, "cross-material-search@1"));
    }
    static Stream<Arguments> budgetExecutors() { return executors().map(args -> { Object[] v = args.get(); return Arguments.of(v[0], v[1], v[2]); }); }

    private ObjectNode arguments(String tool, String path) {
        ObjectNode value = json.createObjectNode(); value.putArray("relativePaths").add(path);
        if (tool.equals("project_cross_material_search")) { value.put("query", "needle"); value.withArray("relativePaths").add("资料 空间/第二 文件.tex"); }
        return value;
    }

    private Fixture fixture(boolean oversized, String oversizedTool) throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root")); Path materials = Files.createDirectories(root.resolve("资料 空间"));
        Files.writeString(materials.resolve("论文 主.tex"), "intro\n\\section{needle}\n");
        Files.writeString(materials.resolve("第二 文件.tex"), "needle\n");
        Files.writeString(materials.resolve("文献 库.bib"), "@article{k,\n title = {t},\n year = {2024}\n}\n");
        Files.writeString(materials.resolve("模型 代码.java"), "package x;\nclass Needle {}\n");
        Files.writeString(materials.resolve("指标 文件.json"), "{\"needle\":1}\n");
        if (oversized) {
            long budget = ResearchToolContracts.byName(oversizedTool).budget().maxBytesInspected();
            String extension = switch (oversizedTool) { case "project_latex_outline", "project_cross_material_search" -> "tex"; case "project_bibtex_audit" -> "bib"; case "project_code_symbols" -> "java"; default -> "json"; };
            String name = "资料 空间/超大." + extension; Files.writeString(root.resolve(name), "x".repeat((int) budget + 1));
            // Replace the normal target with the oversized admitted text file.
            Path target = switch (oversizedTool) { case "project_latex_outline", "project_cross_material_search" -> materials.resolve("论文 主.tex"); case "project_bibtex_audit" -> materials.resolve("文献 库.bib"); case "project_code_symbols" -> materials.resolve("模型 代码.java"); default -> materials.resolve("指标 文件.json"); };
            Files.delete(target); Files.move(root.resolve(name), target);
        }
        ProjectStorageProperties properties = new ProjectStorageProperties(); properties.setLocalServerRoot(root.toString());
        properties.setMaxFileBytes(6L * 1024 * 1024); properties.setMaxTotalBytes(40L * 1024 * 1024);
        ProjectRepository repository = org.mockito.Mockito.mock(ProjectRepository.class);
        Project project = new Project(USER, "Study", ".", root.toRealPath().toString(), "[\"**\"]", "[]");
        when(repository.findByIdAndUserId(PROJECT, USER)).thenReturn(Optional.of(project));
        return new Fixture(new ProjectService(repository, List.of(new LocalServerProjectRootProvider(properties)), properties, json));
    }
    private record Fixture(ProjectService service) { }
}
