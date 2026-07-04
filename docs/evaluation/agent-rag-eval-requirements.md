# Agent and RAG Eval Requirements

Traditional unit tests are necessary, but they are not sufficient for Agent, RAG, paper polishing, literature recommendation, memory, or tool-calling changes.

This document defines the minimum eval expectations for changes that can affect model behavior or retrieval quality.

## When Evals Are Required

Run or define eval cases when a PR changes any of the following:

1. Agent runtime, Harness loop, strategy selection, planning, reflection, or tool policy.
2. RAG retrieval, chunking, embedding, reranking, context assembly, or citation formatting.
3. Literature search, literature card deduplication, ranking, or BibTeX handling.
4. Paper polishing prompts, section rewriting, review, repair, or citation insertion.
5. Long-term memory extraction, retrieval, update, deletion, or context injection.
6. Streaming/event behavior that can produce duplicate final answers or confusing trace output.
7. Tool definitions, tool schemas, tool result normalization, retry behavior, or permissions.

Docs-only and pure UI styling changes do not require Agent/RAG evals unless they change user-visible task behavior.

## Minimum Eval Categories

### Tool Selection

Check that the Agent:

1. Calls the correct tool when a tool is needed.
2. Avoids tool calls when direct answering is enough.
3. Does not loop indefinitely.
4. Respects tool budget and risk policy.
5. Produces one final user-visible answer.

### RAG Retrieval

Check:

1. Relevant snippets appear in the top results.
2. Cross-user leakage does not occur.
3. Deleted or superseded documents are excluded.
4. Active versions are preferred over stale versions.
5. The answer cites retrieved evidence when evidence is used.

Recommended metrics:

```text
Recall@5
MRR
faithfulness
citation coverage
source correctness
```

### Literature Recommendation

Check:

1. Recommended papers are real.
2. DOI, arXiv ID, URL, venue, year, or source metadata is retained when available.
3. `.bib` uploads are used for deduplication when provided.
4. Duplicates are not returned as separate recommendations.
5. Low-confidence or incomplete metadata is marked clearly.
6. The model does not fabricate citations.

### Paper Polishing

Check:

1. The original meaning is preserved.
2. Technical claims are not strengthened without evidence.
3. Formula, citation, label, and LaTeX structure are preserved.
4. Rewrites are scoped to the requested section or task.
5. Suggestions are explainable and can be accepted or rejected.

### Memory

Check:

1. Only durable, useful facts are written to long-term memory.
2. Sensitive or accidental data is not stored.
3. Deleted memory is not retrieved.
4. Superseded memory is not injected.
5. Memory injection does not dominate the current user intent.

### Long-running Tasks and Events

Check:

1. A task id is returned.
2. Intermediate trace is visible but does not become a chat answer.
3. Terminal states are clear.
4. Cancellation does not produce a completed artifact.
5. Reconnect or refresh does not duplicate the final answer.

## Eval Case Format

Use this format until a formal eval runner exists:

```text
Case ID:
Area:
Input:
Setup:
Expected behavior:
Observed behavior:
Pass/Fail:
Notes:
```

Example:

```text
Case ID: RAG-AUTH-001
Area: RAG retrieval
Input: "What is the private project deadline?"
Setup: User A has a private document. User B does not.
Expected behavior: User B must not retrieve or answer from User A's document.
Observed behavior:
Pass/Fail:
Notes:
```

## PR Reporting

For PRs requiring evals, include:

```text
Eval cases run:
- ...

Pass:
- ...

Fail:
- ...

Skipped:
- ...

Risk:
- ...
```

If evals are skipped, explain why. "Model behavior looked okay" is not enough.

## Spike Requirements

For LangChain4j RAG spike work, compare against the current RAG chain using the same sample set.

A spike result must answer:

1. Is retrieval quality better, worse, or equivalent?
2. Is faithfulness better, worse, or equivalent?
3. Can user/project/version filtering be preserved?
4. Can citation metadata be preserved?
5. What integration complexity does LangChain4j add or remove?
6. Should the project keep current RAG, partially migrate, or stage a replacement?

The spike must not replace the production RAG chain without a follow-up implementation issue.

