package com.yanban.core.agent;

/** Deterministic adapters from existing lifecycle sources; no fuzzy/string-similarity mapping is allowed. */
public final class AgentRunStateMappings {

    private AgentRunStateMappings() {
    }

    public static AgentTaskState fromTurn(String turnStatus, boolean controlledPartial) {
        if (AgentTurn.STATUS_RUNNING.equals(turnStatus)) {
            return AgentTaskState.active(AgentTaskStatus.RUNNING, AgentTaskPhase.EXECUTING);
        }
        if (AgentTurn.STATUS_COMPLETED.equals(turnStatus)) {
            return AgentTaskState.completed(controlledPartial ? AgentTaskOutcome.PARTIAL : AgentTaskOutcome.SUCCEEDED);
        }
        if (AgentTurn.STATUS_FAILED.equals(turnStatus)) {
            return AgentTaskState.completed(AgentTaskOutcome.FAILED);
        }
        if (AgentTurn.STATUS_CANCELLED.equals(turnStatus)) {
            return AgentTaskState.completed(AgentTaskOutcome.CANCELLED);
        }
        throw unsupported("turn", turnStatus);
    }

    public static AgentTaskState fromPlan(AgentPlanStatus status, boolean verifiedSuccess, boolean usefulPartial) {
        if (status == null) {
            throw unsupported("plan", null);
        }
        return switch (status) {
            case REVIEWING -> AgentTaskState.active(AgentTaskStatus.PENDING, AgentTaskPhase.PLANNING);
            case RUNNING -> AgentTaskState.active(AgentTaskStatus.RUNNING, AgentTaskPhase.EXECUTING);
            case PAUSED -> AgentTaskState.active(AgentTaskStatus.PAUSED, AgentTaskPhase.PAUSED);
            case COMPLETED -> AgentTaskState.completed(verifiedSuccess
                    ? AgentTaskOutcome.SUCCEEDED
                    : usefulPartial ? AgentTaskOutcome.PARTIAL : AgentTaskOutcome.FAILED);
            case FAILED -> AgentTaskState.completed(AgentTaskOutcome.FAILED);
            case CANCELLED -> AgentTaskState.completed(AgentTaskOutcome.CANCELLED);
        };
    }

    public static AgentTaskState fromPersistedTask(String status, String phase, AgentTaskOutcome terminalOutcome) {
        AgentTaskStatus parsedStatus = AgentTaskStatus.parse(status);
        if (parsedStatus == null) {
            throw unsupported("task status", status);
        }
        AgentTaskPhase parsedPhase;
        try {
            parsedPhase = AgentTaskPhase.valueOf(phase);
        } catch (RuntimeException ex) {
            throw unsupported("task phase", phase);
        }
        return new AgentTaskState(parsedStatus, parsedPhase, terminalOutcome);
    }

    public static AgentTaskState fromRuntimeTerminal(boolean succeeded, boolean usefulPartial) {
        if (succeeded && !usefulPartial) {
            return AgentTaskState.completed(AgentTaskOutcome.SUCCEEDED);
        }
        if (usefulPartial) {
            return AgentTaskState.completed(AgentTaskOutcome.PARTIAL);
        }
        return AgentTaskState.completed(AgentTaskOutcome.FAILED);
    }

    private static IllegalArgumentException unsupported(String source, Object value) {
        return new IllegalArgumentException("unsupported " + source + " state: " + value);
    }
}
