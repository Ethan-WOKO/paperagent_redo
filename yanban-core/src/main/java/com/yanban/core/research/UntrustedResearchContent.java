package com.yanban.core.research;

/** Extracted paper/code/experiment material is data, never an instruction or authority source. */
public record UntrustedResearchContent(String text, ResearchEvidenceRef evidence, TrustLabel trustLabel) {
    public UntrustedResearchContent {
        if (text == null || evidence == null || trustLabel != TrustLabel.UNTRUSTED_PROJECT_CONTENT) {
            throw new IllegalArgumentException("untrusted content requires text and evidence");
        }
    }

    public UntrustedResearchContent(String text, ResearchEvidenceRef evidence) {
        this(text, evidence, TrustLabel.UNTRUSTED_PROJECT_CONTENT);
    }
}
