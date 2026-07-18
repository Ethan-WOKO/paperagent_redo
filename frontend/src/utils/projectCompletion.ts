import type { AgentPlanResponse, SendMessageResponse } from '@/api/agent';

const CONTROLLED_PARTIAL_STOP_REASONS = new Set([
  'TOOL_CALL_BUDGET_EXHAUSTED',
  'MAX_STEPS_BUDGET_EXHAUSTED',
  'MODEL_OUTPUT_TRUNCATED',
  'PLAN_PARTIAL',
]);
const INTERNAL_EVIDENCE_REF_LINE = /^[ \t]*\[projectEvidenceRefs=[^\r\n\]]*\][ \t]*(?:\r?\n|$)/gm;
const INTERNAL_EVIDENCE_REF = /\[projectEvidenceRefs=[^\r\n\]]*\]/g;

export function isControlledProjectPartial(response: SendMessageResponse) {
  return response.completionStatus === 'PARTIAL'
    && response.outcome === 'PARTIAL'
    && response.stopReason != null
    && CONTROLLED_PARTIAL_STOP_REASONS.has(response.stopReason)
    && Boolean(response.assistantContent?.trim());
}

export function withoutInternalProjectEvidenceRefs(content?: string | null) {
  if (!content) return '';
  return content
    .replace(INTERNAL_EVIDENCE_REF_LINE, '')
    .replace(INTERNAL_EVIDENCE_REF, '')
    .trimEnd();
}

export function projectPlanLifecycle(plan: AgentPlanResponse) {
  return plan.status;
}

export function projectPlanExecutionOutcome(plan: AgentPlanResponse) {
  return plan.executionOutcome;
}

export function projectPlanFinalAnswer(plan: AgentPlanResponse) {
  return withoutInternalProjectEvidenceRefs(plan.finalAnswer).trim();
}
