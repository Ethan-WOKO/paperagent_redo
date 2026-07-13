package com.yanban.paper.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.paper.domain.LiteratureCard;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class CrossrefPaperBibMetadataResolverTest {

    @Test
    void parsesPublicationAndConferenceMetadata() {
        LiteratureCard card = new LiteratureCard("hash", "Conference paper");
        card.setDoi("10.1049/example");
        card.setPublicationYear(2023);
        card.setVenue("IET Conference Proceedings");
        PaperBibMetadataResolver.BibMetadata fallback = PaperBibMetadataResolver.BibMetadata.fromCard(card);
        CrossrefPaperBibMetadataResolver resolver = new CrossrefPaperBibMetadataResolver(
                new ObjectMapper(), HttpClient.newHttpClient());

        PaperBibMetadataResolver.BibMetadata metadata = resolver.parse("""
                {
                  "message": {
                    "type": "proceedings-article",
                    "container-title": ["IET Conference Proceedings"],
                    "event": {"name": "IET International Radar Conference 2023"},
                    "volume": "2023",
                    "issue": "47",
                    "page": "2354-2359",
                    "publisher": "Institution of Engineering and Technology (IET)",
                    "published": {"date-parts": [[2024, 4, 4]]}
                  }
                }
                """, fallback, "10.1049/example");

        assertThat(metadata.entryType()).isEqualTo("inproceedings");
        assertThat(metadata.eventTitle()).isEqualTo("IET International Radar Conference 2023");
        assertThat(metadata.volume()).isEqualTo("2023");
        assertThat(metadata.issue()).isEqualTo("47");
        assertThat(metadata.pages()).isEqualTo("2354-2359");
        assertThat(metadata.publicationYear()).isEqualTo(2024);
        assertThat(metadata.url()).isEqualTo("https://doi.org/10.1049/example");
    }
}
