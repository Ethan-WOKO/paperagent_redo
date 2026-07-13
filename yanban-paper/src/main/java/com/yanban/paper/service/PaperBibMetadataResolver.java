package com.yanban.paper.service;

import com.yanban.paper.domain.LiteratureCard;

/** Resolves publication metadata for the small set of references actually cited. */
public interface PaperBibMetadataResolver {

    BibMetadata resolve(LiteratureCard card);

    record BibMetadata(
            String entryType,
            String containerTitle,
            String eventTitle,
            String volume,
            String issue,
            String pages,
            String publisher,
            Integer publicationYear,
            String url
    ) {
        public static BibMetadata fromCard(LiteratureCard card) {
            String venue = card == null ? "" : value(card.getVenue());
            String lowerVenue = venue.toLowerCase(java.util.Locale.ROOT);
            String type = lowerVenue.contains("conference") || lowerVenue.contains("proceedings")
                    ? "inproceedings" : "article";
            String doi = card == null ? "" : normalizeDoi(card.getDoi());
            String url = doi.isBlank() ? (card == null ? "" : value(card.getUrl())) : "https://doi.org/" + doi;
            return new BibMetadata(type, venue, "", "", "", "", "",
                    card == null ? null : card.getPublicationYear(), url);
        }

        private static String value(String value) {
            return value == null ? "" : value.trim();
        }

        private static String normalizeDoi(String doi) {
            return value(doi).toLowerCase(java.util.Locale.ROOT)
                    .replace("https://doi.org/", "")
                    .replace("http://doi.org/", "")
                    .replace("doi:", "")
                    .trim();
        }
    }
}
