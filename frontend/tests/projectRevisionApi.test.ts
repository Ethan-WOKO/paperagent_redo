import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('../src/api/http', () => ({ default: http }));

import {
  applyProjectCandidate,
  exportProjectRevision,
  listProjectRevisions,
  rollbackProjectRevision,
} from '../src/api/project';

describe('Project revision API client', () => {
  beforeEach(() => vi.clearAllMocks());

  it('sends only selected change indexes while authority stays in route and headers', () => {
    const version = 'a'.repeat(64);
    applyProjectCandidate(7, 41, version, [0, 2], 'apply-key-1');

    expect(http.post).toHaveBeenCalledWith('/projects/7/candidates/41/applications',
      { acceptedChangeIndexes: [0, 2] },
      { headers: { 'Idempotency-Key': 'apply-key-1', 'If-Match': version } });
  });

  it('uses real history, rollback, and bounded server export endpoints', () => {
    const version = 'b'.repeat(64);
    listProjectRevisions(7);
    rollbackProjectRevision(7, 12, version, 'rollback-key-1');
    exportProjectRevision(7, 12);

    expect(http.get).toHaveBeenNthCalledWith(1, '/projects/7/revisions');
    expect(http.post).toHaveBeenCalledWith('/projects/7/revisions/12/rollback', {},
      { headers: { 'Idempotency-Key': 'rollback-key-1', 'If-Match': version } });
    expect(http.get).toHaveBeenNthCalledWith(2, '/projects/7/revisions/12/export', { responseType: 'blob' });
  });
});
