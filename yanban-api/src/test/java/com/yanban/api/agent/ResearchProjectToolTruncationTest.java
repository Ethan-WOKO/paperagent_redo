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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchProjectToolTruncationTest {
    @TempDir Path tempDir; private final ObjectMapper json = new ObjectMapper();
    @AfterEach void clear() { ToolExecutionContext.clear(); }

    @ParameterizedTest(name = "truncated:{0}") @MethodSource("tools")
    void preservesOnlyBudgetedEvidenceAfterPriorReliableResult(String tool, BiFunction<ProjectService,ObjectMapper,ToolExecutor> factory) throws Exception {
        ProjectService service = fixture(tool, false); ToolExecutionContext.setCurrentUserId(7L); ToolExecutionContext.setCurrentProjectId(42L); ToolExecutionContext.setResolvedAllowedTools(Set.of(tool));
        var result = factory.apply(service, json).execute(new ToolCall("t", tool, args(tool)));
        assertThat(result.success()).isTrue(); assertThat(result.errorCode()).isNull();
        assertThat(result.output().path("status").asText()).isEqualTo("TRUNCATED"); assertThat(result.output().path("errorCode").asText()).isEqualTo("RESULT_TRUNCATED");
        assertThat(result.output().path("partial").asBoolean()).isTrue(); assertThat(result.output().path("truncated").asBoolean()).isTrue(); assertThat(result.output().path("parseFailed").asBoolean()).isFalse();
        var contract = ResearchToolContracts.byName(tool); var items = result.output().path("items"); var envelope = result.output().path("evidenceRefs");
        assertThat(items.size()).isPositive(); assertThat(items.size()).isLessThanOrEqualTo(contract.budget().maxOutputItems()); assertThat(envelope.size()).isLessThanOrEqualTo(contract.budget().maxEvidenceRefs());
        items.forEach(item -> assertThat(envelope).anySatisfy(e -> assertThat(e).isEqualTo(item.path("content").path("evidence"))));
        if (tool.equals("project_cross_material_search")) items.get(0).path("linkedEvidence").forEach(link -> assertThat(envelope).anySatisfy(e -> assertThat(e).isEqualTo(link)));
        assertThat(envelope).allSatisfy(e -> assertThat(e.path("relativePath").asText()).doesNotContain("large"));
    }

    @Test
    void bibtexPostProcessingLimitIsTruncatedWithRealProvenance() throws Exception {
        ProjectService service = fixture("project_bibtex_audit", true); ToolExecutionContext.setCurrentUserId(7L); ToolExecutionContext.setCurrentProjectId(42L); ToolExecutionContext.setResolvedAllowedTools(Set.of("project_bibtex_audit"));
        ObjectNode args = json.createObjectNode(); args.putArray("relativePaths").add("many.bib"); args.put("includeUnusedEntries", true);
        var result = new ProjectBibtexAuditToolExecutor(service, json).execute(new ToolCall("many", "project_bibtex_audit", args));
        int max = ResearchToolContracts.byName("project_bibtex_audit").budget().maxOutputItems();
        assertThat(result.success()).isTrue(); assertThat(result.output().path("status").asText()).isEqualTo("TRUNCATED");
        assertThat(result.output().path("items").size()).isLessThanOrEqualTo(max); assertThat(result.output().path("evidenceRefs").size()).isLessThanOrEqualTo(400);
        assertThat(result.output().path("items").get(result.output().path("items").size()-1).path("content").path("evidence").path("relativePath").asText()).isEqualTo("many.bib");
    }

    static Stream<Arguments> tools() { return Stream.of(
            Arguments.of("project_latex_outline", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectLatexOutlineToolExecutor::new),
            Arguments.of("project_bibtex_audit", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectBibtexAuditToolExecutor::new),
            Arguments.of("project_code_symbols", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCodeSymbolsToolExecutor::new),
            Arguments.of("project_experiment_summary", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectExperimentSummaryToolExecutor::new),
            Arguments.of("project_cross_material_search", (BiFunction<ProjectService,ObjectMapper,ToolExecutor>) ProjectCrossMaterialSearchToolExecutor::new)); }

    private ObjectNode args(String tool) { ObjectNode n=json.createObjectNode(); n.putArray("relativePaths").add("small."+ext(tool)); if(tool.equals("project_cross_material_search")){n.put("query","needle");n.withArray("relativePaths").add("second.tex").add("large.tex");} else n.withArray("relativePaths").add("large."+ext(tool)); return n; }
    private String ext(String tool) { return switch(tool){case "project_latex_outline"->"tex";case "project_bibtex_audit"->"bib";case "project_code_symbols"->"java";case "project_experiment_summary"->"json";default->"tex";}; }
    private ProjectService fixture(String tool, boolean many) throws Exception {
        Path root=Files.createDirectories(tempDir.resolve(tool+(many?"-many":"")));
        if(many){StringBuilder b=new StringBuilder();for(int i=0;i<305;i++)b.append("@article{k").append(i).append(",\n title={t}\n}\n");Files.writeString(root.resolve("many.bib"),b);}
        else { Files.writeString(root.resolve("small."+ext(tool)), small(tool)); if(tool.equals("project_cross_material_search")) Files.writeString(root.resolve("second.tex"),"needle\n"); long limit=ResearchToolContracts.byName(tool).budget().maxBytesInspected(); Files.writeString(root.resolve("large."+ext(tool)),"x".repeat((int)limit+1)); }
        ProjectStorageProperties p=new ProjectStorageProperties();p.setLocalServerRoot(root.toString());p.setMaxFileBytes(6L*1024*1024);p.setMaxTotalBytes(40L*1024*1024);
        ProjectRepository r=org.mockito.Mockito.mock(ProjectRepository.class);Project project=new Project(7L,"p",".",root.toRealPath().toString(),"[\"**\"]","[]");when(r.findByIdAndUserId(42L,7L)).thenReturn(Optional.of(project));return new ProjectService(r,List.of(new LocalServerProjectRootProvider(p)),p,json);
    }
    private String small(String tool){return switch(tool){case "project_latex_outline"->"\\section{ok}\n";case "project_bibtex_audit"->"@article{k,\n title={t}\n}\n";case "project_code_symbols"->"class A {}\n";case "project_experiment_summary"->"{\"metric\":1}";default->"needle\n";};}
}
