import type {
  AgentPlanResponse,
  EvidenceCategory,
  EvidenceStatus,
  ExternalSourceAccess,
  SynthesisEvidence,
} from '@/api/agent';

export type ResultTone = 'default' | 'success' | 'warning' | 'error' | 'info';

export type ProjectResultMessageKey =
  | 'project.result.status.queued'
  | 'project.result.status.running'
  | 'project.result.status.waitingConfirmation'
  | 'project.result.status.success'
  | 'project.result.status.partial'
  | 'project.result.status.failed'
  | 'project.result.status.timedOut'
  | 'project.result.status.cancelled'
  | 'project.result.status.unavailable'
  | 'project.result.status.unknown'
  | 'project.result.execution.success'
  | 'project.result.execution.partial'
  | 'project.result.execution.failed'
  | 'project.result.execution.timedOut'
  | 'project.result.execution.cancelled'
  | 'project.result.execution.unavailable'
  | 'project.result.execution.notApplicable'
  | 'project.result.execution.pending'
  | 'project.result.task.success'
  | 'project.result.task.partial'
  | 'project.result.task.failed'
  | 'project.result.task.timedOut'
  | 'project.result.task.cancelled'
  | 'project.result.task.pending'
  | 'project.result.answer.verified'
  | 'project.result.answer.supported'
  | 'project.result.answer.inferred'
  | 'project.result.answer.unverified'
  | 'project.result.answer.conflicting'
  | 'project.result.answer.stale'
  | 'project.result.evidence.execution'
  | 'project.result.evidence.project'
  | 'project.result.evidence.openedExternal'
  | 'project.result.evidence.unverifiedExternal'
  | 'project.result.evidence.inference'
  | 'project.result.evidence.unverified'
  | 'project.result.evidence.conflicting'
  | 'project.result.evidence.stale';

export interface ResultPresentation {
  key: ProjectResultMessageKey;
  tone: ResultTone;
}

export type EvidenceDisplayGroup =
  | 'execution'
  | 'project'
  | 'openedExternal'
  | 'unverifiedExternal'
  | 'inference'
  | 'unverified'
  | 'conflicting'
  | 'stale';

export interface EvidenceGroupPresentation extends ResultPresentation {
  group: EvidenceDisplayGroup;
  evidence: SynthesisEvidence[];
}

const EVIDENCE_GROUP_ORDER: EvidenceDisplayGroup[] = [
  'execution',
  'project',
  'openedExternal',
  'unverifiedExternal',
  'inference',
  'unverified',
  'conflicting',
  'stale',
];

function normalized(value?: string | null) {
  return value?.trim().toUpperCase() || '';
}

export function executionOutcomePresentation(value?: string | null): ResultPresentation {
  switch (normalized(value)) {
    case 'SUCCESS':
    case 'SUCCEEDED':
    case 'COMPLETED':
      return { key: 'project.result.execution.success', tone: 'success' };
    case 'PARTIAL':
      return { key: 'project.result.execution.partial', tone: 'warning' };
    case 'FAILED':
      return { key: 'project.result.execution.failed', tone: 'error' };
    case 'TIMED_OUT':
      return { key: 'project.result.execution.timedOut', tone: 'error' };
    case 'CANCELLED':
      return { key: 'project.result.execution.cancelled', tone: 'warning' };
    case 'UNAVAILABLE':
    case 'SANDBOX_UNAVAILABLE':
      return { key: 'project.result.execution.unavailable', tone: 'warning' };
    case 'NOT_APPLICABLE':
      return { key: 'project.result.execution.notApplicable', tone: 'default' };
    default:
      return { key: 'project.result.execution.pending', tone: 'info' };
  }
}

export function taskOutcomePresentation(value?: string | null): ResultPresentation {
  switch (normalized(value)) {
    case 'SUCCESS':
    case 'COMPLETED':
      return { key: 'project.result.task.success', tone: 'success' };
    case 'PARTIAL':
      return { key: 'project.result.task.partial', tone: 'warning' };
    case 'TIMED_OUT':
      return { key: 'project.result.task.timedOut', tone: 'error' };
    case 'CANCELLED':
      return { key: 'project.result.task.cancelled', tone: 'warning' };
    case 'FAILED':
    case 'UNAVAILABLE':
      return { key: 'project.result.task.failed', tone: 'error' };
    default:
      return { key: 'project.result.task.pending', tone: 'info' };
  }
}

export function answerStatusPresentation(value?: EvidenceStatus | null): ResultPresentation {
  switch (normalized(value)) {
    case 'VERIFIED':
      return { key: 'project.result.answer.verified', tone: 'success' };
    case 'SUPPORTED':
      return { key: 'project.result.answer.supported', tone: 'success' };
    case 'INFERRED':
      return { key: 'project.result.answer.inferred', tone: 'info' };
    case 'CONFLICTING':
      return { key: 'project.result.answer.conflicting', tone: 'error' };
    case 'STALE':
      return { key: 'project.result.answer.stale', tone: 'warning' };
    default:
      return { key: 'project.result.answer.unverified', tone: 'warning' };
  }
}

export function planUserStatusPresentation(plan: AgentPlanResponse, waitingForConfirmation: boolean): ResultPresentation {
  if (waitingForConfirmation) return { key: 'project.result.status.waitingConfirmation', tone: 'warning' };
  const lifecycle = normalized(plan.status);
  if (lifecycle === 'PENDING') return { key: 'project.result.status.queued', tone: 'info' };
  if (lifecycle === 'RUNNING' || lifecycle === 'REVIEWING') {
    return { key: 'project.result.status.running', tone: 'info' };
  }

  const task = normalized(plan.taskOutcome || plan.finalSynthesisInput?.taskOutcome);
  const execution = normalized(plan.executionOutcome || plan.finalSynthesisInput?.executionOutcome);
  const terminal = task || execution || lifecycle;
  if (terminal === 'SUCCESS' || terminal === 'COMPLETED') return { key: 'project.result.status.success', tone: 'success' };
  if (terminal === 'PARTIAL') return { key: 'project.result.status.partial', tone: 'warning' };
  if (terminal === 'TIMED_OUT') return { key: 'project.result.status.timedOut', tone: 'error' };
  if (terminal === 'CANCELLED') return { key: 'project.result.status.cancelled', tone: 'warning' };
  if (terminal === 'UNAVAILABLE') return { key: 'project.result.status.unavailable', tone: 'warning' };
  if (terminal === 'FAILED') return { key: 'project.result.status.failed', tone: 'error' };
  return { key: 'project.result.status.unknown', tone: 'default' };
}

export function evidenceDisplayGroup(evidence: SynthesisEvidence): EvidenceDisplayGroup {
  if (evidence.status === 'CONFLICTING') return 'conflicting';
  if (evidence.status === 'STALE') return 'stale';
  if (evidence.category === 'EXECUTION_FACT') return 'execution';
  if (evidence.category === 'VERIFIED_PROJECT_EVIDENCE') return 'project';
  if (evidence.category === 'EXTERNAL_SOURCE') {
    return evidence.externalAccess === 'OPENED' ? 'openedExternal' : 'unverifiedExternal';
  }
  if (evidence.category === 'INFERENCE') return 'inference';
  return 'unverified';
}

function evidenceGroupPresentation(group: EvidenceDisplayGroup, evidence: SynthesisEvidence[]): EvidenceGroupPresentation {
  const values: Record<EvidenceDisplayGroup, Omit<EvidenceGroupPresentation, 'evidence'>> = {
    execution: { group, key: 'project.result.evidence.execution', tone: 'success' },
    project: { group, key: 'project.result.evidence.project', tone: 'success' },
    openedExternal: { group, key: 'project.result.evidence.openedExternal', tone: 'info' },
    unverifiedExternal: { group, key: 'project.result.evidence.unverifiedExternal', tone: 'warning' },
    inference: { group, key: 'project.result.evidence.inference', tone: 'info' },
    unverified: { group, key: 'project.result.evidence.unverified', tone: 'warning' },
    conflicting: { group, key: 'project.result.evidence.conflicting', tone: 'error' },
    stale: { group, key: 'project.result.evidence.stale', tone: 'warning' },
  };
  return { ...values[group], evidence };
}

export function groupSynthesisEvidence(evidence: SynthesisEvidence[] = []): EvidenceGroupPresentation[] {
  const groups = new Map<EvidenceDisplayGroup, SynthesisEvidence[]>();
  evidence.forEach((item) => {
    const group = evidenceDisplayGroup(item);
    groups.set(group, [...(groups.get(group) || []), item]);
  });
  return EVIDENCE_GROUP_ORDER
    .filter((group) => groups.has(group))
    .map((group) => evidenceGroupPresentation(group, groups.get(group) || []));
}

export function effectivePlanResult(plan: AgentPlanResponse) {
  return {
    executionOutcome: plan.finalSynthesisInput?.executionOutcome || plan.executionOutcome,
    taskOutcome: plan.finalSynthesisInput?.taskOutcome || plan.taskOutcome,
    answerStatus: plan.finalSynthesisInput?.answerStatus || plan.answerStatus,
  };
}

export function hasTechnicalEvidenceFields(item: SynthesisEvidence) {
  return Boolean(item.id || item.basisRefs.length || item.projectVersion || item.hash || item.path
    || item.sourceType || item.executionFact?.provider || item.executionFact?.command.length
    || item.executionFact?.status || item.executionFact?.exitCode != null);
}

export function externalAccessIsUnverified(value: ExternalSourceAccess) {
  return value === 'SEARCH_SUMMARY' || value === 'UNKNOWN';
}

export function evidenceCategoryIsExecution(value: EvidenceCategory) {
  return value === 'EXECUTION_FACT';
}
