import { describe, expect, it } from 'vitest';
import type { AgentPlanResponse } from '../src/api/agent';
import { requiresSandboxConfirmation, sandboxConfirmationStepCount } from '../src/utils/projectSandboxConfirmation';

function plan(status: string, type = 'SANDBOX_EXECUTE', stepStatus = 'PENDING'): AgentPlanResponse {
  return {
    id: 66,
    sessionId: 113,
    goal: 'Run Java in the sandbox',
    summary: null,
    status,
    ragDisabled: true,
    skillId: null,
    errorMessage: null,
    createdAt: '2026-07-19T22:09:07+08:00',
    updatedAt: '2026-07-19T22:09:09+08:00',
    startedAt: null,
    finishedAt: null,
    executionOutcome: status,
    finalAnswer: null,
    steps: [{
      id: 189,
      stepKey: 'sandbox-run',
      sortOrder: 1,
      title: 'Execute Java file in sandbox',
      description: 'Run src/main/java/xhs_1111.java.',
      type,
      dependencies: [],
      allowedTools: ['sandbox_execute'],
      successCriteria: 'Return stdout.',
      status: stepStatus,
      attemptCount: 0,
      result: null,
      errorMessage: null,
      startedAt: null,
      finishedAt: null,
    }],
  };
}

describe('Project sandbox confirmation', () => {
  it('requires confirmation only for a pending sandbox step held in REVIEWING', () => {
    expect(requiresSandboxConfirmation(plan('REVIEWING'))).toBe(true);
    expect(sandboxConfirmationStepCount(plan('REVIEWING'))).toBe(1);
    expect(requiresSandboxConfirmation(plan('RUNNING'))).toBe(false);
    expect(requiresSandboxConfirmation(plan('REVIEWING', 'ANALYSIS'))).toBe(false);
    expect(requiresSandboxConfirmation(plan('REVIEWING', 'SANDBOX_EXECUTE', 'COMPLETED'))).toBe(false);
  });
});
