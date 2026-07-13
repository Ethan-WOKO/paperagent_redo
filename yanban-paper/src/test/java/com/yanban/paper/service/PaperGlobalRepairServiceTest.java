package com.yanban.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.paper.domain.PaperSection;
import com.yanban.paper.domain.PaperSectionRepository;
import com.yanban.paper.domain.PaperTask;
import com.yanban.paper.latex.LatexDocument;
import com.yanban.paper.latex.LatexMaskingService;
import com.yanban.paper.latex.LatexSection;
import com.yanban.paper.latex.LatexSectionRole;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaperGlobalRepairServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void persistsOneVerifiedLocalProseRepair() {
        String original = "The method is introduced here. The next section begins without a transition.";
        String replacement = "The method is introduced here. The next section builds on this formulation.";
        PaperPromptService prompts = mock(PaperPromptService.class);
        when(prompts.render(eq("global-review-repair"), anyMap())).thenReturn("repair");
        when(prompts.render(eq("global-review-repair-verify"), anyMap())).thenReturn("verify");
        PaperModelClient model = (system, user, temperature, maxTokens) -> "repair".equals(user)
                ? json(Map.of(
                        "originalAnchor", original,
                        "replacementText", replacement,
                        "reason", "Added a local transition."))
                : json(Map.of("resolved", true, "reason", "The transition is now explicit."));
        PaperStorageService storage = mock(PaperStorageService.class);
        when(storage.storeArtifact(anyLong(), eq("section_polished"), anyString(), any(), anyString()))
                .thenReturn("paper/section-repaired.tex");
        PaperSectionRepository sections = mock(PaperSectionRepository.class);
        PaperGlobalRepairService service = new PaperGlobalRepairService(
                prompts, model, storage, sections, new LatexMaskingService(), objectMapper);
        PaperSection stored = storedSection(0);

        PaperGlobalRepairService.RepairSummary summary = service.repair(
                task(), document(original), List.of(stored), List.of(issue("TRANSITION", "NARRATIVE_GAP", true, original)));

        assertThat(summary.attemptedCount()).isEqualTo(1);
        assertThat(summary.resolvedCount()).isEqualTo(1);
        assertThat(summary.remainingCount()).isZero();
        assertThat(summary.issues()).singleElement().satisfies(value -> {
            assertThat(value.get("resolutionStatus")).isEqualTo("RESOLVED");
            assertThat(value.get("repairAttempts")).isEqualTo(1);
        });
        assertThat(stored.getPolishedObjectKey()).isEqualTo("paper/section-repaired.tex");
        verify(storage).storeArtifact(anyLong(), eq("section_polished"), anyString(),
                eq(replacement.getBytes(java.nio.charset.StandardCharsets.UTF_8)), anyString());
        verify(sections).save(stored);
    }

    @Test
    void formulaAndSymbolFindingsRemainReportOnly() {
        PaperPromptService prompts = mock(PaperPromptService.class);
        PaperModelClient model = (system, user, temperature, maxTokens) -> {
            throw new AssertionError("The model must not be called for formula findings.");
        };
        PaperStorageService storage = mock(PaperStorageService.class);
        PaperSectionRepository sections = mock(PaperSectionRepository.class);
        PaperGlobalRepairService service = new PaperGlobalRepairService(
                prompts, model, storage, sections, new LatexMaskingService(), objectMapper);
        String formula = "The covariance is $Q=R\\odot G$.";

        PaperGlobalRepairService.RepairSummary summary = service.repair(
                task(), document(formula), List.of(storedSection(0)),
                List.of(issue("FORMULA", "FORMULA_PROSE_MISMATCH", false, formula)));

        assertThat(summary.attemptedCount()).isZero();
        assertThat(summary.resolvedCount()).isZero();
        assertThat(summary.issues()).singleElement()
                .satisfies(value -> assertThat(value.get("resolutionStatus")).isEqualTo("REPORT_ONLY"));
        verify(storage, never()).storeArtifact(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void rejectsProseRepairThatChangesInlineFormula() {
        String original = "The repeated definition uses $Q=R\\odot G$ in this section.";
        String replacement = "The repeated definition uses $Q=R+G$ in this section.";
        PaperPromptService prompts = mock(PaperPromptService.class);
        when(prompts.render(eq("global-review-repair"), anyMap())).thenReturn("repair");
        PaperModelClient model = (system, user, temperature, maxTokens) -> json(Map.of(
                "originalAnchor", original,
                "replacementText", replacement,
                "reason", "Changed the formula."));
        PaperStorageService storage = mock(PaperStorageService.class);
        PaperSectionRepository sections = mock(PaperSectionRepository.class);
        PaperGlobalRepairService service = new PaperGlobalRepairService(
                prompts, model, storage, sections, new LatexMaskingService(), objectMapper);

        PaperGlobalRepairService.RepairSummary summary = service.repair(
                task(), document(original), List.of(storedSection(0)),
                List.of(issue("DUPLICATION", "DUPLICATED_CONTENT", true, original)));

        assertThat(summary.resolvedCount()).isZero();
        assertThat(summary.issues()).singleElement().satisfies(value -> {
            assertThat(value.get("resolutionStatus")).isEqualTo("REPORT_ONLY");
            assertThat(value.get("repairAttempts")).isEqualTo(PaperGlobalRepairService.MAX_REPAIR_ROUNDS);
        });
        verify(storage, never()).storeArtifact(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void discardsCandidateWhenFocusedReviewNeverPasses() {
        String original = "This duplicated prose should be replaced after focused review.";
        String replacement = "This prose now refers to the earlier explanation.";
        PaperPromptService prompts = mock(PaperPromptService.class);
        when(prompts.render(eq("global-review-repair"), anyMap())).thenReturn("repair");
        when(prompts.render(eq("global-review-repair-verify"), anyMap())).thenReturn("verify");
        AtomicInteger calls = new AtomicInteger();
        PaperModelClient model = (system, user, temperature, maxTokens) -> {
            calls.incrementAndGet();
            return "repair".equals(user)
                    ? json(Map.of("originalAnchor", original, "replacementText", replacement))
                    : json(Map.of("resolved", false, "reason", "The issue remains."));
        };
        PaperStorageService storage = mock(PaperStorageService.class);
        PaperSectionRepository sections = mock(PaperSectionRepository.class);
        PaperGlobalRepairService service = new PaperGlobalRepairService(
                prompts, model, storage, sections, new LatexMaskingService(), objectMapper);
        PaperSection stored = storedSection(0);

        PaperGlobalRepairService.RepairSummary summary = service.repair(
                task(), document(original), List.of(stored), List.of(issue("DUPLICATION", "DUPLICATED_CONTENT", true, original)));

        assertThat(summary.resolvedCount()).isZero();
        assertThat(summary.remainingCount()).isEqualTo(1);
        assertThat(summary.issues()).singleElement().satisfies(value -> {
            assertThat(value.get("resolutionStatus")).isEqualTo("REPORT_ONLY");
            assertThat(value.get("repairAttempts")).isEqualTo(PaperGlobalRepairService.MAX_REPAIR_ROUNDS);
        });
        assertThat(calls.get()).isEqualTo(PaperGlobalRepairService.MAX_REPAIR_ROUNDS * 2);
        assertThat(stored.getPolishedObjectKey()).isNull();
        verify(storage, never()).storeArtifact(anyLong(), anyString(), anyString(), any(), anyString());
    }

    private Map<String, Object> issue(String type, String ruleId, boolean autoFixAllowed, String quote) {
        return Map.of(
                "type", type,
                "ruleId", ruleId,
                "sectionIds", List.of(0),
                "severity", "major",
                "message", "Focused issue.",
                "suggestedFix", "Repair locally.",
                "autoFixAllowed", autoFixAllowed,
                "evidence", List.of(Map.of("sectionOrder", 0, "equationLabel", "", "quote", quote)));
    }

    private PaperTask task() {
        return new PaperTask(7L, "Paper", "main.tex", "paper/main.tex", "RUNNING", "en", "GLOBAL_REVIEW", null);
    }

    private PaperSection storedSection(int order) {
        PaperSection section = new PaperSection(1L, "main.tex", order, 1, "Method", "METHOD", 1.0,
                "parser", 0, 100);
        section.setReviewJson("{}");
        return section;
    }

    private LatexDocument document(String raw) {
        LatexSection section = new LatexSection(0, 1, "section", true, "Method", LatexSectionRole.METHOD,
                0, raw.length(), raw);
        return new LatexDocument("main.tex", "Paper", List.of(), List.of(), "", "", List.of(section),
                List.of(), List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
