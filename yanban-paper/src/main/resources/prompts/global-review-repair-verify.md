# Whole-Paper Focused Repair Verification

Target language: {{targetLanguage}}
Paper title: {{paperTitle}}
Target section order: {{targetSectionOrder}}

Original verified issue:
{{issue}}

Original anchor:
{{originalAnchor}}

Replacement:
{{replacementText}}

Repaired local context:
{{repairedExcerpt}}

Decide whether this one local replacement fully resolves the stated issue without changing technical meaning or introducing a new inconsistency.

Rules:
- Partial improvement is not enough.
- Reject changes that weaken, strengthen, or invent a technical claim.
- Reject changes that leave the original duplication, transition gap, logic problem, terminology conflict, or claim mismatch unresolved.
- Formula, citation, reference, label, and LaTeX preservation is checked separately by the backend.
- Return strict JSON only.

Return:
{
  "resolved": true,
  "reason": "short verification explanation"
}
