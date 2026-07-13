package com.yanban.api.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.agent.AgentPlan;
import com.yanban.core.agent.AgentPlanStep;
import com.yanban.core.agent.AgentPlanStepStatus;
import com.yanban.core.agent.AgentSession;
import com.yanban.core.model.ChatMessage;
import com.yanban.core.model.ChatModelProvider;
import com.yanban.core.model.ChatRequest;
import com.yanban.core.model.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlanStepVerifier {

    private static final Logger log = LoggerFactory.getLogger(PlanStepVerifier.class);

    private final ChatModelProvider modelProvider;
    private final ObjectMapper objectMapper;

    public PlanStepVerifier(@Qualifier("chatModelProvider") ChatModelProvider modelProvider,
                            ObjectMapper objectMapper) {
        this.modelProvider = modelProvider;
        this.objectMapper = objectMapper;
    }

    public VerificationResult verify(VerificationRequest request) {
        if (!StringUtils.hasText(request.successCriteria())) {
            return VerificationResult.passed("No explicit success criteria were provided.");
        }
        try {
            ChatResponse response = callVerifier(request, List.of(
                    ChatMessage.system(buildVerifierSystemPrompt()),
                    ChatMessage.user(buildVerifierUserMessage(request))), 256);
            String content = response == null || response.message() == null ? null : response.message().content();
            logVerifierResponse(request, response, content, 1);
            if (!StringUtils.hasText(content)) {
                return VerificationResult.inconclusive("Verifier returned an empty response.");
            }
            try {
                return parseVerification(content);
            } catch (Exception ex) {
                log.warn("Plan step verifier returned invalid JSON stepKey={} attempt=1 finishReason={} error={}",
                        request.step().getStepKey(), response == null ? null : response.finishReason(), abbreviate(ex.getMessage(), 300));
                ChatResponse repaired = callVerifier(request, List.of(
                        ChatMessage.system(buildRepairSystemPrompt()),
                        ChatMessage.user(buildRepairUserMessage(request, content))), 160);
                String repairedContent = repaired == null || repaired.message() == null ? null : repaired.message().content();
                logVerifierResponse(request, repaired, repairedContent, 2);
                try {
                    return parseVerification(repairedContent);
                } catch (Exception repairError) {
                    log.warn("Plan step verifier repair returned invalid JSON stepKey={} attempt=2 finishReason={} error={}",
                            request.step().getStepKey(), repaired == null ? null : repaired.finishReason(),
                            abbreviate(repairError.getMessage(), 300));
                    return VerificationResult.inconclusive("Verifier returned invalid JSON after one bounded repair."
                            + finishReasonSuffix(response, repaired));
                }
            }
        } catch (Exception ex) {
            log.warn("Plan step verification failed stepKey={}", request.step().getStepKey(), ex);
            return VerificationResult.inconclusive("Verifier error: " + abbreviate(ex.getMessage(), 300));
        }
    }

    private String buildVerifierSystemPrompt() {
        return """
                You are an independent verifier for a Plan-and-Execute agent.
                Judge only whether the candidate result satisfies the current step success criteria.
                Do not require future steps to be completed.
                Be strict about missing evidence, missing requested artifacts, and vague placeholder answers.
                Return one compact JSON object only. Keep reason under 120 characters and at most 3 missing items:
                {"passed":true,"reason":"brief reason","missing":[]}
                """;
    }

    private String buildRepairSystemPrompt() {
        return """
                Repair a verifier decision into one compact JSON object only.
                Do not repeat the candidate result. Use exactly these fields:
                {"passed":true,"reason":"under 120 characters","missing":[]}
                """;
    }

    private String buildRepairUserMessage(VerificationRequest request, String invalidOutput) {
        return "Success criteria:\n" + abbreviate(request.successCriteria(), 500)
                + "\n\nCandidate summary:\n" + abbreviate(request.candidateResult(), 1400)
                + "\n\nInvalid verifier output to repair:\n" + abbreviate(invalidOutput, 400);
    }

    private ChatResponse callVerifier(VerificationRequest request, List<ChatMessage> messages, int maxTokens) {
        return modelProvider.chat(new ChatRequest(
                request.session().getModelProviderSnapshot(), request.session().getModelSnapshot(), messages,
                0.0, maxTokens, null, request.apiKey(), request.apiUrl(), ChatRequest.ResponseFormat.jsonObject(),
                ChatRequest.Thinking.disabled(), request.traceId()));
    }

    private void logVerifierResponse(VerificationRequest request, ChatResponse response, String content, int attempt) {
        ChatResponse.Usage usage = response == null ? null : response.usage();
        log.info("Plan step verifier response stepKey={} attempt={} finishReason={} promptTokens={} completionTokens={} contentLength={}",
                request.step().getStepKey(), attempt, response == null ? null : response.finishReason(),
                usage == null ? null : usage.promptTokens(), usage == null ? null : usage.completionTokens(),
                content == null ? 0 : content.length());
    }

    private String finishReasonSuffix(ChatResponse first, ChatResponse second) {
        String firstReason = first == null ? null : first.finishReason();
        String secondReason = second == null ? null : second.finishReason();
        return " [firstFinishReason=" + blankToDefault(firstReason, "unknown")
                + ", repairFinishReason=" + blankToDefault(secondReason, "unknown") + "]";
    }

    private String buildVerifierUserMessage(VerificationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Overall goal:\n")
                .append(request.plan().getGoal())
                .append("\n\nCurrent step:\n")
                .append("- id: ").append(request.step().getStepKey()).append("\n")
                .append("- title: ").append(blankToDefault(request.step().getTitle(), "")).append("\n")
                .append("- type: ").append(blankToDefault(request.step().getType(), "")).append("\n")
                .append("- description: ").append(request.step().getDescription()).append("\n")
                .append("- success criteria: ").append(request.successCriteria()).append("\n\n")
                .append("Completed dependency results:\n");
        boolean hasDependencyResult = false;
        for (AgentPlanStep dependency : completedDependencyResults(request)) {
            if (StringUtils.hasText(dependency.getResult())) {
                hasDependencyResult = true;
                sb.append("## ").append(dependency.getStepKey()).append(" ")
                        .append(blankToDefault(dependency.getTitle(), "")).append("\n")
                        .append(abbreviate(dependency.getResult(), 1200))
                        .append("\n\n");
            }
        }
        if (!hasDependencyResult) {
            sb.append("None.\n\n");
        }
        sb.append("Candidate result to verify:\n")
                .append(abbreviate(request.candidateResult(), 3000));
        return sb.toString();
    }

    private VerificationResult parseVerification(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(stripCodeFence(raw));
        Boolean passed = readBoolean(root, "passed", "success", "satisfied");
        if (passed == null) {
            return VerificationResult.inconclusive("Verifier response did not include a boolean passed field.");
        }
        String reason = firstText(root, "reason", "rationale", "explanation");
        String evidence = abbreviate(firstText(root, "evidence", "supportingEvidence"), 240);
        String missingItems = readMissingItems(root.path("missing"));
        if (!StringUtils.hasText(missingItems)) {
            missingItems = readMissingItems(root.path("missingItems"));
        }
        if (!StringUtils.hasText(missingItems)) {
            missingItems = readMissingItems(root.path("missing_items"));
        }
        if (!passed && StringUtils.hasText(missingItems)) {
            reason = appendSentence(reason, "Missing: " + missingItems);
        }
        if (!StringUtils.hasText(reason)) {
            reason = passed ? "Candidate result satisfies the success criteria."
                    : "Candidate result does not satisfy the success criteria.";
        }
        return new VerificationResult(passed, true, abbreviate(reason, 240), evidence);
    }

    private List<AgentPlanStep> completedDependencyResults(VerificationRequest request) {
        if (request == null || request.step() == null || request.allSteps() == null) {
            return List.of();
        }
        List<String> dependencyKeys = readStringList(request.step().getDependenciesJson());
        if (dependencyKeys.isEmpty()) {
            return List.of();
        }
        return request.allSteps().stream()
                .filter(item -> AgentPlanStepStatus.COMPLETED.name().equals(item.getStatus())
                        || AgentPlanStepStatus.DEGRADED.name().equals(item.getStatus()))
                .filter(item -> dependencyKeys.contains(item.getStepKey()))
                .filter(item -> StringUtils.hasText(item.getResult()))
                .toList();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String stripCodeFence(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        cleaned = cleaned.replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first >= 0 && last >= first) {
            return cleaned.substring(first, last + 1).trim();
        }
        return cleaned;
    }

    private Boolean readBoolean(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isTextual()) {
                String value = node.asText("").trim().toLowerCase();
                if ("true".equals(value) || "yes".equals(value) || "pass".equals(value) || "passed".equals(value)) {
                    return true;
                }
                if ("false".equals(value) || "no".equals(value) || "fail".equals(value) || "failed".equals(value)) {
                    return false;
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return "";
    }

    private String readMissingItems(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("").trim();
        }
        if (!node.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return String.join("; ", values);
    }

    private String appendSentence(String value, String sentence) {
        if (!StringUtils.hasText(value)) {
            return sentence;
        }
        return value.endsWith(".") ? value + " " + sentence : value + ". " + sentence;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String blankToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record VerificationRequest(
            AgentPlan plan,
            AgentSession session,
            AgentPlanStep step,
            List<AgentPlanStep> allSteps,
            String candidateResult,
            String apiKey,
            String apiUrl,
            String traceId
    ) {
        public VerificationRequest(AgentPlan plan,
                                   AgentSession session,
                                   AgentPlanStep step,
                                   List<AgentPlanStep> allSteps,
                                   String candidateResult,
                                   String apiKey) {
            this(plan, session, step, allSteps, candidateResult, apiKey, null, null);
        }

        public String successCriteria() {
            return step == null ? null : step.getSuccessCriteria();
        }

    }

    public record VerificationResult(boolean passed, boolean conclusive, String reason, String evidence) {
        public static VerificationResult passed(String reason) {
            return new VerificationResult(true, true, reason, null);
        }

        public static VerificationResult failed(String reason) {
            return new VerificationResult(false, true, reason, null);
        }

        public static VerificationResult inconclusive(String reason) {
            return new VerificationResult(true, false, reason, null);
        }
    }
}
