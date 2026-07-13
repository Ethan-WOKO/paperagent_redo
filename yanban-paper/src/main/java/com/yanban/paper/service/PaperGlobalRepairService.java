package com.yanban.paper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.paper.domain.PaperSection;
import com.yanban.paper.domain.PaperSectionRepository;
import com.yanban.paper.domain.PaperTask;
import com.yanban.paper.latex.LatexDocument;
import com.yanban.paper.latex.LatexLintIssue;
import com.yanban.paper.latex.LatexMaskingService;
import com.yanban.paper.latex.LatexSection;
import com.yanban.paper.latex.MaskedLatexText;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Applies bounded, verified prose-only repairs for whole-paper review findings. */
@Service
public class PaperGlobalRepairService {

    static final int MAX_REPAIR_ROUNDS = 3;
    static final int MAX_REPAIR_ISSUES = 3;
    private static final Set<String> SAFE_TYPES = Set.of("TRANSITION", "LOGIC", "CLAIM_RESULT_MISMATCH", "DUPLICATION");
    private static final Pattern STRUCTURAL_COMMAND = Pattern.compile(
            "\\\\(?:section|subsection|subsubsection|paragraph|subparagraph)\\*?\\s*\\{[^{}]+}"
                    + "|\\\\(?:begin|end)\\s*\\{[^{}]+}"
                    + "|\\\\(?:label|ref|eqref|cref|Cref|autoref|pageref|cite|citep|citet|parencite|textcite|autocite)\\*?(?:\\s*\\[[^]]*]){0,2}\\s*\\{[^{}]+}"
                    + "|\\\\[A-Za-z@]+\\*?");

    private final PaperPromptService promptService;
    private final PaperModelClient modelClient;
    private final PaperStorageService storageService;
    private final PaperSectionRepository sections;
    private final LatexMaskingService maskingService;
    private final ObjectMapper objectMapper;

    public PaperGlobalRepairService(PaperPromptService promptService,
                                    PaperModelClient modelClient,
                                    PaperStorageService storageService,
                                    PaperSectionRepository sections,
                                    LatexMaskingService maskingService,
                                    ObjectMapper objectMapper) {
        this.promptService = promptService;
        this.modelClient = modelClient;
        this.storageService = storageService;
        this.sections = sections;
        this.maskingService = maskingService;
        this.objectMapper = objectMapper;
    }

    public RepairSummary repair(PaperTask task,
                                LatexDocument document,
                                List<PaperSection> storedSections,
                                List<Map<String, Object>> issues) {
        Map<Integer, String> sectionTexts = document.sections().stream()
                .collect(Collectors.toMap(LatexSection::orderIndex, LatexSection::rawText,
                        (left, right) -> left, LinkedHashMap::new));
        Map<Integer, PaperSection> storedByOrder = (storedSections == null ? List.<PaperSection>of() : storedSections).stream()
                .collect(Collectors.toMap(PaperSection::getOrderIndex, value -> value,
                        (left, right) -> left, LinkedHashMap::new));
        List<Map<String, Object>> updatedIssues = new ArrayList<>();
        int attempted = 0;
        int resolved = 0;

        for (Map<String, Object> rawIssue : issues == null ? List.<Map<String, Object>>of() : issues) {
            Map<String, Object> issue = new LinkedHashMap<>(rawIssue);
            if (!eligible(issue)) {
                issue.put("resolutionStatus", "REPORT_ONLY");
                issue.put("repairAttempts", 0);
                updatedIssues.add(issue);
                continue;
            }
            if (attempted >= MAX_REPAIR_ISSUES) {
                issue.put("resolutionStatus", "REPORT_ONLY");
                issue.put("repairAttempts", 0);
                issue.put("resolutionReason", "The bounded global-repair budget was used by earlier findings.");
                updatedIssues.add(issue);
                continue;
            }
            Integer targetOrder = targetSectionOrder(issue, sectionTexts);
            PaperSection stored = targetOrder == null ? null : storedByOrder.get(targetOrder);
            if (targetOrder == null || stored == null) {
                issue.put("resolutionStatus", "REPORT_ONLY");
                issue.put("repairAttempts", 0);
                issue.put("resolutionReason", "No unique stored section was available for a safe local repair.");
                updatedIssues.add(issue);
                continue;
            }

            attempted++;
            String currentText = sectionTexts.get(targetOrder);
            String feedback = "";
            boolean accepted = false;
            int attempts = 0;
            for (int round = 1; round <= MAX_REPAIR_ROUNDS; round++) {
                attempts = round;
                RepairCandidate candidate = proposeRepair(task, issue, targetOrder, currentText, feedback);
                if (!candidate.valid()) {
                    feedback = candidate.message();
                    continue;
                }
                LocalPatch patch = validateAndApply(currentText, candidate.originalAnchor(), candidate.replacementText());
                if (!patch.applied()) {
                    feedback = patch.message();
                    continue;
                }
                Verification verification = verifyRepair(task, issue, targetOrder, candidate, patch.text());
                if (!verification.resolved()) {
                    feedback = verification.reason();
                    continue;
                }
                persistRepair(task, stored, patch.text());
                sectionTexts.put(targetOrder, patch.text());
                issue.put("resolutionStatus", "RESOLVED");
                issue.put("repairAttempts", round);
                issue.put("resolutionReason", verification.reason());
                accepted = true;
                resolved++;
                break;
            }
            if (!accepted) {
                issue.put("resolutionStatus", "REPORT_ONLY");
                issue.put("repairAttempts", attempts);
                issue.put("resolutionReason", blankToDefault(feedback,
                        "The proposed local repair did not pass deterministic checks and focused review."));
            }
            updatedIssues.add(issue);
        }
        return new RepairSummary(List.copyOf(updatedIssues), attempted, resolved,
                Math.max(0, updatedIssues.size() - resolved));
    }

    private boolean eligible(Map<String, Object> issue) {
        if (!Boolean.TRUE.equals(issue.get("autoFixAllowed"))) return false;
        String type = string(issue.get("type")).toUpperCase(Locale.ROOT);
        String rule = string(issue.get("ruleId")).toUpperCase(Locale.ROOT);
        if ("NOTATION".equals(type)) return "TERM_CONFLICT".equals(rule);
        return SAFE_TYPES.contains(type);
    }

    private Integer targetSectionOrder(Map<String, Object> issue, Map<Integer, String> sectionTexts) {
        List<Integer> evidenceOrders = new ArrayList<>();
        Object evidence = issue.get("evidence");
        if (evidence instanceof List<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> item)) continue;
                Integer order = integer(item.get("sectionOrder"));
                if (order != null && sectionTexts.containsKey(order)) evidenceOrders.add(order);
            }
        }
        if (!evidenceOrders.isEmpty()) return evidenceOrders.get(evidenceOrders.size() - 1);
        Object sectionIds = issue.get("sectionIds");
        if (sectionIds instanceof List<?> values) {
            for (int index = values.size() - 1; index >= 0; index--) {
                Integer order = integer(values.get(index));
                if (order != null && sectionTexts.containsKey(order)) return order;
            }
        }
        return null;
    }

    private RepairCandidate proposeRepair(PaperTask task,
                                          Map<String, Object> issue,
                                          int targetOrder,
                                          String currentText,
                                          String feedback) {
        try {
            String prompt = promptService.render("global-review-repair", Map.of(
                    "targetLanguage", blankToDefault(task.getTargetLanguage(), "en"),
                    "paperTitle", blankToDefault(task.getTitle(), "Untitled paper"),
                    "targetSectionOrder", targetOrder,
                    "issue", toJson(issue),
                    "sectionExcerpt", focusedExcerpt(currentText, issue),
                    "previousFeedback", blankToDefault(feedback, "None.")));
            Map<String, Object> response = readMap(modelClient.complete(
                    "Return strict JSON for one minimal prose-only replacement. Do not modify formulas or LaTeX structure.",
                    prompt, 0.1, 2048));
            String originalAnchor = string(response.get("originalAnchor"));
            String replacementText = string(response.get("replacementText"));
            if (originalAnchor.length() < 20 || replacementText.isBlank()) {
                return RepairCandidate.invalid("The repair response did not contain one stable local replacement.");
            }
            return new RepairCandidate(true, originalAnchor, replacementText,
                    stringOr(response.get("reason"), "Local prose repair proposed."));
        } catch (Exception ex) {
            return RepairCandidate.invalid(rootMessage(ex));
        }
    }

    private LocalPatch validateAndApply(String text, String anchor, String replacement) {
        if (occurrences(text, anchor) != 1) {
            return LocalPatch.failed("The proposed repair anchor was not unique in the target section.");
        }
        if (!structuralCommands(anchor).equals(structuralCommands(replacement))) {
            return LocalPatch.failed("The proposed repair changed protected LaTeX structure.");
        }
        MaskedLatexText originalMasked = maskingService.mask(anchor);
        MaskedLatexText replacementMasked = maskingService.mask(replacement);
        if (!originalMasked.placeholders().equals(replacementMasked.placeholders())) {
            return LocalPatch.failed("The proposed repair changed a formula, citation, reference, label, or protected environment.");
        }
        int start = text.indexOf(anchor);
        String candidate = text.substring(0, start) + replacement + text.substring(start + anchor.length());
        boolean blocker = maskingService.lint(candidate).stream()
                .anyMatch(issue -> issue.severity() == LatexLintIssue.Severity.BLOCKER);
        if (blocker) return LocalPatch.failed("The proposed repair did not pass LaTeX lint checks.");
        return LocalPatch.applied(candidate);
    }

    private Verification verifyRepair(PaperTask task,
                                      Map<String, Object> issue,
                                      int targetOrder,
                                      RepairCandidate candidate,
                                      String repairedText) {
        try {
            Map<String, Object> replacementEvidence = Map.of(
                    "evidence", List.of(Map.of(
                            "sectionOrder", targetOrder,
                            "quote", candidate.replacementText())));
            String prompt = promptService.render("global-review-repair-verify", Map.of(
                    "targetLanguage", blankToDefault(task.getTargetLanguage(), "en"),
                    "paperTitle", blankToDefault(task.getTitle(), "Untitled paper"),
                    "targetSectionOrder", targetOrder,
                    "issue", toJson(issue),
                    "originalAnchor", candidate.originalAnchor(),
                    "replacementText", candidate.replacementText(),
                    "repairedExcerpt", focusedExcerpt(repairedText, replacementEvidence)));
            Map<String, Object> response = readMap(modelClient.complete(
                    "Return strict JSON only. Accept only a complete local resolution that preserves the manuscript's technical meaning.",
                    prompt, 0.0, 1024));
            boolean resolved = booleanValue(response.get("resolved"));
            return new Verification(resolved, stringOr(response.get("reason"),
                    resolved ? "Focused review passed." : "Focused review did not confirm the repair."));
        } catch (Exception ex) {
            return new Verification(false, rootMessage(ex));
        }
    }

    private void persistRepair(PaperTask task,
                               PaperSection section,
                               String repairedText) {
        String objectKey = storageService.storeArtifact(task.getUserId(), "section_polished",
                "section-" + section.getOrderIndex() + "-global-review.tex",
                repairedText.getBytes(StandardCharsets.UTF_8), "application/x-tex; charset=UTF-8");
        section.setPolishedObjectKey(objectKey);
        sections.save(section);
    }

    private String focusedExcerpt(String sectionText, Map<String, Object> issue) {
        String text = sectionText == null ? "" : sectionText;
        String quote = "";
        Object evidence = issue.get("evidence");
        if (evidence instanceof List<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> item)) continue;
                String candidate = string(item.get("quote"));
                if (!candidate.isBlank() && text.contains(candidate)) quote = candidate;
            }
        }
        if (quote.isBlank()) return truncate(text, 7000);
        int start = Math.max(0, text.indexOf(quote) - 2500);
        int end = Math.min(text.length(), text.indexOf(quote) + quote.length() + 2500);
        return text.substring(start, end);
    }

    private List<String> structuralCommands(String value) {
        List<String> commands = new ArrayList<>();
        Matcher matcher = STRUCTURAL_COMMAND.matcher(value == null ? "" : value);
        while (matcher.find()) commands.add(matcher.group().replaceAll("\\s+", " ").trim());
        return commands;
    }

    private int occurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private Map<String, Object> readMap(String value) {
        try {
            String source = value == null ? "{}" : value.trim();
            int start = source.indexOf('{');
            int end = source.lastIndexOf('}');
            return new LinkedHashMap<>(objectMapper.readValue(
                    start >= 0 && end > start ? source.substring(start, end + 1) : "{}",
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception ignored) { return "{}"; }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return blankToDefault(current.getMessage(), current.getClass().getSimpleName());
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String stringOr(Object value, String fallback) { String result = string(value); return result.isBlank() ? fallback : result; }
    private Integer integer(Object value) { if (value instanceof Number number) return number.intValue(); try { return value == null ? null : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; } }
    private boolean booleanValue(Object value) { return value instanceof Boolean bool ? bool : Boolean.parseBoolean(string(value)); }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String truncate(String value, int max) { return value == null || value.length() <= max ? (value == null ? "" : value) : value.substring(0, max) + " ..."; }

    public record RepairSummary(List<Map<String, Object>> issues, int attemptedCount, int resolvedCount,
                                int remainingCount) {}
    private record RepairCandidate(boolean valid, String originalAnchor, String replacementText, String message) {
        private static RepairCandidate invalid(String message) { return new RepairCandidate(false, "", "", message); }
    }
    private record LocalPatch(boolean applied, String text, String message) {
        private static LocalPatch applied(String text) { return new LocalPatch(true, text, ""); }
        private static LocalPatch failed(String message) { return new LocalPatch(false, "", message); }
    }
    private record Verification(boolean resolved, String reason) {}
}
