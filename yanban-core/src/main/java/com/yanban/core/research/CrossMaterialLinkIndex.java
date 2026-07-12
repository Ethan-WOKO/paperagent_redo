package com.yanban.core.research;

import java.util.List;

/** Link between paper, code, configuration, and/or experiment evidence. */
public record CrossMaterialLinkIndex(String concept, List<IndexedProvenance> linkedMaterials,
                                     String relation, IndexFreshness freshness) {
    public CrossMaterialLinkIndex {
        if (concept == null || concept.isBlank() || linkedMaterials == null || linkedMaterials.size() < 2
                || relation == null || relation.isBlank() || freshness == null) {
            throw new IllegalArgumentException("cross-material link requires two or more indexed materials");
        }
        linkedMaterials = List.copyOf(linkedMaterials);
    }
}
