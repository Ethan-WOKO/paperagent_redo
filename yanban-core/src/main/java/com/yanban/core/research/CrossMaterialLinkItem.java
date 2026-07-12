package com.yanban.core.research;

import java.util.List;

/** Explicit cross-paper/code/experiment concept relation with at least two provenance anchors. */
public record CrossMaterialLinkItem(String concept, String relation, List<ResearchEvidenceRef> linkedEvidence,
                                    UntrustedResearchContent content) implements ResearchToolItem {
    public CrossMaterialLinkItem {
        if (concept == null || concept.isBlank() || relation == null || relation.isBlank() || content == null
                || linkedEvidence == null || linkedEvidence.size() < 2) {
            throw new IllegalArgumentException("cross-material item requires concept, relation, content, and two evidence refs");
        }
        linkedEvidence = List.copyOf(linkedEvidence);
    }

    @Override public ResearchToolItemType itemType() { return ResearchToolItemType.CROSS_MATERIAL_LINK; }
}
