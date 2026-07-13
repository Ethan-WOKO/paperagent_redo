# Whole-Paper Focused Repair

Target language: {{targetLanguage}}
Paper title: {{paperTitle}}
Target section order: {{targetSectionOrder}}

Verified issue:
{{issue}}

Target section excerpt:
{{sectionExcerpt}}

Previous failed-attempt feedback:
{{previousFeedback}}

Propose one minimal local prose replacement that resolves only this issue.

Rules:
- `originalAnchor` must be copied exactly and verbatim from the target section excerpt.
- The anchor must be unique, stable, and contain enough surrounding prose for deterministic replacement.
- Preserve the technical meaning, claims, numbers, citations, references, labels, equations, inline math, and LaTeX environments exactly.
- Do not add a new claim, result, experiment, citation, formula, label, or reference.
- Do not rewrite the whole section.
- If the issue cannot be resolved without changing protected technical content, return empty anchors.
- Return strict JSON only.

Return:
{
  "originalAnchor": "exact prose copied from the target section",
  "replacementText": "minimal corrected prose",
  "reason": "short explanation"
}
