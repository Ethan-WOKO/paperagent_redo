package com.yanban.paper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.paper.domain.LiteratureCard;
import com.yanban.paper.domain.Suggestion;
import com.yanban.paper.latex.LatexBibEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PaperCitationApplyServiceTest {

    private final PaperCitationApplyService service = new PaperCitationApplyService(new ObjectMapper());

    @Test
    void reusesExistingDoiKeyAndOnlyAppliesAcceptedSuggestions() {
        LiteratureCard card = card(11L, "10.1000/ABC", "A useful paper");
        Suggestion accepted = suggestion(101L, "ACCEPTED", true, "A unique sentence in the paper.");
        Suggestion rejected = suggestion(102L, "REJECTED", true, "Another unique sentence in the paper.");
        LatexBibEntry existing = new LatexBibEntry("userKey", "article",
                Map.of("doi", "10.1000/abc", "title", "A useful paper"), "@article{userKey}\n", "refs.bib");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "A unique sentence in the paper.", "@article{userKey}\n", "refs.bib",
                Map.of("userKey", existing), List.of(accepted, rejected), Map.of(101L, List.of(card), 102L, List.of(card)));

        assertThat(result.polishedTex()).contains("\\cite{userKey}");
        assertThat(result.mergedBib()).isEqualTo("@article{userKey}\n");
        assertThat(result.newBibKeys()).isEmpty();
    }

    @Test
    void createsStableMarkedEntryAndIsIdempotent() {
        LiteratureCard card = card(12L, "10.1000/new", "A New Method");
        Suggestion accepted = suggestion(103L, "ACCEPTED", true, "A unique sentence in the paper.");
        String tex = "A unique sentence in the paper.";

        PaperCitationApplyService.CitationApplyResult first = service.apply(tex, "", "refs.bib", Map.of(),
                List.of(accepted), Map.of(103L, List.of(card)));
        PaperCitationApplyService.CitationApplyResult second = service.apply(tex, "", "refs.bib", Map.of(),
                List.of(accepted), Map.of(103L, List.of(card)));

        assertThat(first.polishedTex()).isEqualTo(second.polishedTex()).contains("\\cite{yb_");
        assertThat(first.mergedBib()).isEqualTo(second.mergedBib())
                .contains("YANBAN_RECOMMENDED_BEGIN")
                .doesNotContain("note={");
    }

    @Test
    void doesNotModifyAmbiguousAnchorOrAddUnusedNewEntry() {
        LiteratureCard card = card(13L, "10.1000/ambiguous", "Ambiguous Paper");
        Suggestion accepted = suggestion(104L, "ACCEPTED", true, "Repeated sentence in the paper.");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "Repeated sentence in the paper.\nRepeated sentence in the paper.", "", "refs.bib", Map.of(),
                List.of(accepted), Map.of(104L, List.of(card)));

        assertThat(result.polishedTex()).doesNotContain("\\cite{");
        assertThat(result.mergedBib()).doesNotContain("@article{");
        assertThat(result.manualPatches()).extracting(item -> item.get("status")).contains("ANCHOR_AMBIGUOUS");
    }

    @Test
    void appliesCitationAfterWhitespaceAndPunctuationNormalization() {
        LiteratureCard card = card(14L, "10.1000/normalized", "Normalized Match");
        Suggestion accepted = suggestion(105L, "ACCEPTED", true, "A stable sentence supports this claim.");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "A stable sentence supports this claim !", "", "refs.bib", Map.of(),
                List.of(accepted), Map.of(105L, List.of(card)));

        assertThat(result.polishedTex()).contains("\\cite{yb_");
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("matchMode")).isEqualTo("NORMALIZED"));
    }

    @Test
    void appliesHighConfidenceSentenceMatchInsideRequestedSection() {
        LiteratureCard card = card(15L, "10.1000/fuzzy", "Fuzzy Match");
        String anchor = "A polarimetric radar uses waveform diversity to suppress coherent mainlobe interference in contested environments.";
        Suggestion accepted = suggestion(106L, "ACCEPTED", true, anchor, 0, "Introduction");
        String polished = "\\section{Introduction}\n"
                + "A polarimetric radar uses waveform diversity to suppress strong coherent mainlobe interference in challenging contested environments.\n"
                + "\\section{Method}\nThe method is described here.";

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                polished, "", "refs.bib", Map.of(), List.of(accepted), Map.of(106L, List.of(card)));

        assertThat(result.polishedTex()).contains("contested environments \\cite{yb_");
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("matchMode")).isEqualTo("FUZZY"));
    }

    @Test
    void replacesProtectedSlotMarkerWhenPolishingChangedTheOriginalAnchor() {
        LiteratureCard first = card(16L, "10.1000/marker-a", "Marker Evidence A");
        LiteratureCard second = card(17L, "10.1000/marker-b", "Marker Evidence B");
        Suggestion accepted = suggestion(107L, "ACCEPTED", true, "The original sentence that no longer exists after polishing.");
        String polished = "The polished sentence expresses the claim more clearly \\yanbancitationslot{107}.";

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                polished, "", "refs.bib", Map.of(), List.of(accepted), Map.of(107L, List.of(first, second)));

        assertThat(result.polishedTex())
                .doesNotContain("yanbancitationslot")
                .containsPattern("\\\\cite\\{yb_[^,}]+,yb_[^}]+}");
        assertThat(result.newBibKeys()).hasSize(2);
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("matchMode")).isEqualTo("PROTECTED_SLOT"));
    }

    @Test
    void mergesProtectedSlotWithAdjacentCitationAndDeduplicatesExistingKey() {
        LiteratureCard existingCard = card(18L, null, "Existing Evidence");
        LiteratureCard newCard = card(19L, "10.1000/new-evidence", "New Evidence");
        Suggestion accepted = suggestion(108L, "ACCEPTED", true, "The claim already has one citation.");
        LatexBibEntry existing = new LatexBibEntry("ref1", "article",
                Map.of("title", "Existing Evidence"), "@article{ref1,title={Existing Evidence}}", "refs.bib");
        String polished = "The claim already has one citation \\cite{ref1} \\yanbancitationslot{108}.";

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                polished, existing.rawText(), "refs.bib", Map.of("ref1", existing), List.of(accepted),
                Map.of(108L, List.of(existingCard, newCard)));

        assertThat(result.polishedTex())
                .containsPattern("\\\\cite\\{ref1,yb_[^}]+}")
                .doesNotContain("\\cite{ref1} \\cite{")
                .doesNotContain("yanbancitationslot");
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("matchMode")).isEqualTo("PROTECTED_SLOT_MERGED"));
    }

    @Test
    void recommendedMarkersEncloseEntryAndConferenceVenueUsesProceedingsType() {
        LiteratureCard card = card(20L, "10.1000/conference", "Conference Evidence");
        card.setVenue("IET Conference Proceedings");
        Suggestion accepted = suggestion(109L, "ACCEPTED", true, "A unique conference claim anchor.");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "A unique conference claim anchor.", "", "refs.bib", Map.of(), List.of(accepted),
                Map.of(109L, List.of(card)));

        String bib = result.novelBib();
        assertThat(bib.indexOf("YANBAN_RECOMMENDED_BEGIN")).isLessThan(bib.indexOf("@inproceedings{"));
        assertThat(bib.indexOf("@inproceedings{")).isLessThan(bib.indexOf("YANBAN_RECOMMENDED_END"));
        assertThat(bib).contains("booktitle={IET Conference Proceedings}");
    }

    @Test
    void writesResolvedVolumeIssuePagesPublisherAndDoiUrl() {
        LiteratureCard card = card(201L, "10.1000/complete", "Complete Metadata Evidence");
        card.setUrl("https://openalex.org/W1");
        PaperBibMetadataResolver resolver = ignored -> new PaperBibMetadataResolver.BibMetadata(
                "article", "IEEE Transactions on Testing", "", "57", "6", "3552-3562",
                "IEEE", 2021, "https://doi.org/10.1000/complete");
        PaperCitationApplyService metadataService = new PaperCitationApplyService(new ObjectMapper(), resolver);
        Suggestion accepted = suggestion(201L, "ACCEPTED", true, "A stable metadata citation anchor.");

        PaperCitationApplyService.CitationApplyResult result = metadataService.apply(
                "A stable metadata citation anchor.", "", "refs.bib", Map.of(), List.of(accepted),
                Map.of(201L, List.of(card)));

        assertThat(result.novelBib())
                .contains("journal={IEEE Transactions on Testing}")
                .contains("volume={57}")
                .contains("number={6}")
                .contains("pages={3552--3562}")
                .contains("publisher={IEEE}")
                .contains("url={https://doi.org/10.1000/complete}")
                .doesNotContain("openalex.org");
    }

    @Test
    void distinguishesNewReferencesFromVerifiedExistingCitationLocations() {
        LiteratureCard card = card(202L, "10.1000/existing", "Existing Citation Evidence");
        LatexBibEntry existing = new LatexBibEntry("refExisting", "article",
                Map.of("doi", "10.1000/existing", "title", "Existing Citation Evidence"),
                "@article{refExisting,doi={10.1000/existing}}", "refs.bib");
        Suggestion accepted = suggestion(202L, "ACCEPTED", true, "The claim already cites the evidence.");
        String tex = "The claim already cites the evidence \\cite{refExisting} \\yanbancitationslot{202}.";

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                tex, existing.rawText(), "refs.bib", Map.of("refExisting", existing), List.of(accepted),
                Map.of(202L, List.of(card)));

        assertThat(result.newReferenceCount()).isZero();
        assertThat(result.reusedExistingReferenceCount()).isEqualTo(1);
        assertThat(result.changedCitationLocationCount()).isZero();
        assertThat(result.verifiedExistingCitationLocationCount()).isEqualTo(1);
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("citationChanged")).isEqualTo(false));
    }

    @Test
    void reusesUploadedJournalKeyForLikelyConferenceVersion() {
        LiteratureCard conference = card(
                203L, "10.1109/conference.2022.1", "Polarimetric FDA-MIMO Radar Detection");
        conference.setAuthors("[\"Massimo Rosamilia\",\"Lan Lan\",\"Augusto Aubry\",\"Antonio De Maio\"]");
        conference.setPublicationYear(2022);
        LatexBibEntry journal = new LatexBibEntry("ref12", "article", Map.of(
                "title", "Adaptive Target Detection With Polarimetric FDA-MIMO Radar",
                "author", "Lan, Lan and Rosamilia, Massimo and Aubry, Augusto and De Maio, Antonio and Liao, Guisheng and Xu, Jingwei",
                "year", "2023",
                "doi", "10.1109/taes.2022.1"), "@article{ref12}", "refs.bib");
        Suggestion accepted = suggestion(203L, "ACCEPTED", true, "A stable detection citation anchor.");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "A stable detection citation anchor.", journal.rawText(), "refs.bib",
                Map.of("ref12", journal), List.of(accepted), Map.of(203L, List.of(conference)));

        assertThat(result.polishedTex()).contains("\\cite{ref12}");
        assertThat(result.newReferenceCount()).isZero();
        assertThat(result.novelBib()).isEmpty();
        assertThat(result.bibliography()).singleElement().satisfies(item -> {
            assertThat(item.get("bibKey")).isEqualTo("ref12");
            assertThat(item.get("reuseReason")).isEqualTo("POSSIBLE_CONFERENCE_JOURNAL_VERSION");
        });
    }

    @Test
    void doesNotMergeRelatedTitlesWhenAuthorsDoNotOverlap() {
        LiteratureCard candidate = card(
                204L, "10.1109/conference.2022.2", "Polarimetric FDA-MIMO Radar Detection");
        candidate.setAuthors("[\"Alice Smith\",\"Carol Jones\"]");
        candidate.setPublicationYear(2022);
        LatexBibEntry existing = new LatexBibEntry("ref12", "article", Map.of(
                "title", "Adaptive Target Detection With Polarimetric FDA-MIMO Radar",
                "author", "Lan, Lan and Rosamilia, Massimo and Aubry, Augusto and De Maio, Antonio",
                "year", "2023"), "@article{ref12}", "refs.bib");
        Suggestion accepted = suggestion(204L, "ACCEPTED", true, "A different stable detection anchor.");

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "A different stable detection anchor.", existing.rawText(), "refs.bib",
                Map.of("ref12", existing), List.of(accepted), Map.of(204L, List.of(candidate)));

        assertThat(result.polishedTex()).contains("\\cite{yb_").doesNotContain("\\cite{ref12}");
        assertThat(result.newReferenceCount()).isEqualTo(1);
        assertThat(result.novelBib()).contains("Polarimetric FDA-MIMO Radar Detection");
    }

    @Test
    void appliesApprovedClosureReplacementAndCitationAtomically() throws Exception {
        LiteratureCard card = card(21L, "10.1000/closure", "Closure Evidence");
        String original = "Prior work studies polarimetric target estimation in challenging environments.";
        String replacement = "Prior work studies polarimetric target estimation in radar environments.";
        Suggestion accepted = suggestion(110L, "ACCEPTED", true, replacement, 0, "Introduction");
        Map<String, Object> patch = new ObjectMapper().readValue(accepted.getPatchJson(), Map.class);
        patch.put("citationClosure", Map.of(
                "status", "SUPPORTED",
                "operation", "NARROW",
                "originalAnchor", original,
                "replacementText", replacement,
                "citationAnchor", replacement));
        accepted.setPatchJson(new ObjectMapper().writeValueAsString(patch));

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                "\\section{Introduction}\n" + original,
                "", "refs.bib", Map.of(), List.of(accepted), Map.of(110L, List.of(card)));

        assertThat(result.polishedTex())
                .doesNotContain(original)
                .contains("Prior work studies polarimetric target estimation in radar environments \\cite{yb_");
        assertThat(result.appliedPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("matchMode")).isEqualTo("CLOSURE_NARROW"));
        assertThat(result.mergedBib()).contains("YANBAN_RECOMMENDED_BEGIN");
    }

    @Test
    void leavesOriginalTextAndBibliographyUntouchedWhenClosureCitationAnchorIsInvalid() throws Exception {
        LiteratureCard card = card(22L, "10.1000/closure-invalid", "Invalid Closure Evidence");
        String original = "Prior work studies polarimetric target estimation in challenging environments.";
        String replacement = "Prior work studies polarimetric target estimation in radar environments.";
        Suggestion accepted = suggestion(111L, "ACCEPTED", true, replacement, 0, "Introduction");
        Map<String, Object> patch = new ObjectMapper().readValue(accepted.getPatchJson(), Map.class);
        patch.put("citationClosure", Map.of(
                "status", "SUPPORTED",
                "operation", "NARROW",
                "originalAnchor", original,
                "replacementText", replacement,
                "citationAnchor", "This clause was never inserted into the replacement text."));
        accepted.setPatchJson(new ObjectMapper().writeValueAsString(patch));

        String tex = "\\section{Introduction}\n" + original;
        PaperCitationApplyService.CitationApplyResult result = service.apply(
                tex, "", "refs.bib", Map.of(), List.of(accepted), Map.of(111L, List.of(card)));

        assertThat(result.polishedTex()).isEqualTo(tex);
        assertThat(result.mergedBib()).isEmpty();
        assertThat(result.manualPatches()).singleElement()
                .satisfies(item -> assertThat(item.get("status")).isEqualTo("CLOSURE_CITATION_ANCHOR_NOT_FOUND"));
    }

    @Test
    void appliesExistingAcceptedCitationsBeforeEarlierClosureSuggestions() throws Exception {
        LiteratureCard existingCard = card(23L, null, "Existing Radar Evidence");
        LiteratureCard closureCard = card(24L, "10.1000/ordered-closure", "Closure Radar Evidence");
        LatexBibEntry existing = new LatexBibEntry(
                "ref1", "article", Map.of("title", "Existing Radar Evidence"),
                "@article{ref1,title={Existing Radar Evidence}}", "refs.bib");
        String baseline = "Prior work studies radar estimation.";
        Suggestion closure = suggestion(100L, "ACCEPTED", true,
                "Prior work studies polarimetric radar estimation \\cite{ref1}.");
        Map<String, Object> closurePatch = new ObjectMapper().readValue(closure.getPatchJson(), Map.class);
        closurePatch.put("citationClosure", Map.of(
                "status", "SUPPORTED",
                "operation", "NARROW",
                "originalAnchor", "Prior work studies radar estimation \\cite{ref1}.",
                "replacementText", "Prior work studies polarimetric radar estimation \\cite{ref1}.",
                "citationAnchor", "Prior work studies polarimetric radar estimation \\cite{ref1}."));
        closure.setPatchJson(new ObjectMapper().writeValueAsString(closurePatch));
        Suggestion initialAccepted = suggestion(200L, "ACCEPTED", true, baseline);

        PaperCitationApplyService.CitationApplyResult result = service.apply(
                baseline,
                existing.rawText(),
                "refs.bib",
                Map.of("ref1", existing),
                List.of(closure, initialAccepted),
                Map.of(100L, List.of(closureCard), 200L, List.of(existingCard)));

        assertThat(result.polishedTex())
                .containsPattern("Prior work studies polarimetric radar estimation \\\\cite\\{ref1,yb_[^}]+}\\.");
        assertThat(result.appliedPatches()).hasSize(2);
        assertThat(result.manualPatches()).isEmpty();
    }

    private Suggestion suggestion(Long id, String status, boolean applicable, String anchor) {
        return suggestion(id, status, applicable, anchor, null, null);
    }

    private Suggestion suggestion(Long id, String status, boolean applicable, String anchor, Integer sectionOrder, String sectionTitle) {
        Suggestion suggestion = new Suggestion(1L, "ADVOCACY", "RelatedWork", "Use the evidence.");
        ReflectionTestUtils.setField(suggestion, "id", id);
        suggestion.setStatus(status);
        suggestion.setApplicable(applicable);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("contentType", "A");
        patch.put("anchor", anchor);
        if (sectionOrder != null) patch.put("sectionOrder", sectionOrder);
        if (sectionTitle != null) patch.put("sectionTitle", sectionTitle);
        try {
            suggestion.setPatchJson(new ObjectMapper().writeValueAsString(patch));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return suggestion;
    }

    private LiteratureCard card(Long id, String doi, String title) {
        LiteratureCard card = new LiteratureCard("hash-" + id, title);
        ReflectionTestUtils.setField(card, "id", id);
        card.setDoi(doi);
        card.setAuthors("[\"Alice Smith\"]");
        card.setPublicationYear(2024);
        card.setVenue("Journal");
        return card;
    }
}
