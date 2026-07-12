package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.api.project.LocalServerProjectRootProvider;
import com.yanban.api.project.Project;
import com.yanban.api.project.ProjectRepository;
import com.yanban.api.project.ProjectService;
import com.yanban.api.project.ProjectStorageProperties;
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

class ResearchProjectToolEmptyMalformedTest {
    @TempDir Path tempDir; private final ObjectMapper json = new ObjectMapper();
    @AfterEach void clear() { ToolExecutionContext.clear(); }

    @ParameterizedTest(name = "empty:{0}") @MethodSource("tools")
    void returnsEmptyOnlyForValidNoFindingInputs(String tool, BiFunction<ProjectService,ObjectMapper,ToolExecutor> factory, String path) throws Exception {
        ProjectService service = service(false); authorize(tool);
        var result = factory.apply(service, json).execute(new ToolCall("e", tool, args(tool, path, false)));
        assertThat(result.success()).isTrue(); assertThat(result.output().path("status").asText()).isEqualTo("EMPTY");
        assertThat(result.output().path("items")).isEmpty(); assertThat(result.output().path("evidenceRefs")).isEmpty();
        assertThat(result.output().has("errorCode")).isFalse(); assertThat(result.output().path("partial").asBoolean()).isFalse();
        assertThat(result.output().path("truncated").asBoolean()).isFalse(); assertThat(result.output().path("parseFailed").asBoolean()).isFalse();
    }

    @ParameterizedTest(name = "malformed:{0}") @MethodSource("tools")
    void rejectsMalformedOrUnsupportedInputsWithoutPseudoComplete(String tool, BiFunction<ProjectService,ObjectMapper,ToolExecutor> factory, String path) throws Exception {
        ProjectService service = service(true); authorize(tool);
        var result = factory.apply(service, json).execute(new ToolCall("m", tool, args(tool, path, true)));
        if (result.success()) {
            assertThat(result.output().path("status").asText()).isEqualTo("PARSE_FAILED");
            assertThat(result.output().path("items")).isEmpty(); assertThat(result.output().path("evidenceRefs")).isEmpty();
        } else { assertThat(result.errorCode().name()).isEqualTo("VALIDATION_ERROR"); assertThat(result.output()).isNull(); assertThat(result.evidenceRefs()).isEmpty(); }
    }

    static Stream<Arguments> tools() { return Stream.of(
            Arguments.of("project_latex_outline", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectLatexOutlineToolExecutor::new, "empty.tex"),
            Arguments.of("project_bibtex_audit", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectBibtexAuditToolExecutor::new, "empty.bib"),
            Arguments.of("project_code_symbols", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCodeSymbolsToolExecutor::new, "empty.java"),
            Arguments.of("project_experiment_summary", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectExperimentSummaryToolExecutor::new, "empty.csv"),
            Arguments.of("project_cross_material_search", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCrossMaterialSearchToolExecutor::new, "empty.tex")); }

    private ObjectNode args(String tool, String path, boolean malformed) {
        ObjectNode node = json.createObjectNode(); node.putArray("relativePaths").add(malformed && tool.equals("project_code_symbols") ? "bad.txt" : malformed && tool.equals("project_experiment_summary") ? "bad.json" : path);
        if (tool.equals("project_cross_material_search")) node.put("query", malformed ? " " : "absent");
        if (tool.equals("project_experiment_summary") && !malformed) node.putArray("metricNames").add("missing");
        return node;
    }
    private void authorize(String tool) { ToolExecutionContext.setCurrentUserId(7L); ToolExecutionContext.setCurrentProjectId(42L); ToolExecutionContext.setResolvedAllowedTools(Set.of(tool)); }
    private ProjectService service(boolean malformed) throws Exception {
        Path root = Files.createDirectories(tempDir.resolve(malformed ? "bad" : "empty"));
        Files.writeString(root.resolve("empty.tex"), malformed ? "\\section{bad" : "plain prose\n");
        Files.writeString(root.resolve("empty.bib"), malformed ? "@article{bad," : "@article{ok,\n title = {t},\n author = {a},\n year = {2024}\n}\n");
        Files.writeString(root.resolve("empty.java"), "// no declarations\n"); Files.writeString(root.resolve("bad.txt"), "unsupported\n");
        Files.writeString(root.resolve("empty.csv"), malformed ? "a,b\n1\n" : "metric\n1\n");
        Files.writeString(root.resolve("bad.json"), "{bad");
        ProjectStorageProperties props = new ProjectStorageProperties(); props.setLocalServerRoot(root.toString());
        ProjectRepository repo = org.mockito.Mockito.mock(ProjectRepository.class); Project project = new Project(7L,"p",".",root.toRealPath().toString(),"[\"**\"]","[]");
        when(repo.findByIdAndUserId(42L,7L)).thenReturn(Optional.of(project)); return new ProjectService(repo,List.of(new LocalServerProjectRootProvider(props)),props,json);
    }
}
