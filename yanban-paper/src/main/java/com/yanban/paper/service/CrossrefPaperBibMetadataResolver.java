package com.yanban.paper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.paper.domain.LiteratureCard;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Fail-soft Crossref lookup used only when exporting accepted citations. */
@Service
public class CrossrefPaperBibMetadataResolver implements PaperBibMetadataResolver {

    private static final String ENDPOINT = "https://api.crossref.org/works/";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, BibMetadata> cache = new ConcurrentHashMap<>();
    private volatile long unavailableUntilMillis;

    @Autowired
    public CrossrefPaperBibMetadataResolver(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    CrossrefPaperBibMetadataResolver(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public BibMetadata resolve(LiteratureCard card) {
        BibMetadata fallback = BibMetadata.fromCard(card);
        String doi = normalizeDoi(card == null ? null : card.getDoi());
        if (doi.isBlank()) return fallback;
        if (System.currentTimeMillis() < unavailableUntilMillis) return fallback;
        return cache.computeIfAbsent(doi, ignored -> fetch(doi, fallback));
    }

    private BibMetadata fetch(String doi, BibMetadata fallback) {
        try {
            String encoded = URLEncoder.encode(doi, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + encoded))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .header("User-Agent", "YanbanPaper/1.0 (bibliography metadata lookup)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                unavailableUntilMillis = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
                return fallback;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) return fallback;
            return parse(response.body(), fallback, doi);
        } catch (Exception ignored) {
            unavailableUntilMillis = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
            return fallback;
        }
    }

    BibMetadata parse(String json, BibMetadata fallback, String doi) {
        try {
            JsonNode message = objectMapper.readTree(json).path("message");
            if (!message.isObject()) return fallback;
            String container = firstText(message.path("container-title"));
            String eventTitle = text(message.path("event"), "name");
            String type = entryType(text(message, "type"), container, eventTitle, fallback.entryType());
            Integer year = publicationYear(message);
            return new BibMetadata(
                    type,
                    firstNonBlank(container, fallback.containerTitle()),
                    eventTitle,
                    text(message, "volume"),
                    text(message, "issue"),
                    text(message, "page"),
                    text(message, "publisher"),
                    year == null ? fallback.publicationYear() : year,
                    "https://doi.org/" + doi
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Integer publicationYear(JsonNode message) {
        for (String field : new String[] {"published-print", "published-online", "published", "issued"}) {
            JsonNode parts = message.path(field).path("date-parts");
            if (parts.isArray() && !parts.isEmpty() && parts.get(0).isArray() && !parts.get(0).isEmpty()) {
                int year = parts.get(0).get(0).asInt(0);
                if (year > 0) return year;
            }
        }
        return null;
    }

    private String entryType(String crossrefType, String container, String eventTitle, String fallback) {
        String value = (crossrefType + " " + container + " " + eventTitle).toLowerCase(Locale.ROOT);
        return value.contains("proceedings") || value.contains("conference") ? "inproceedings" : fallback;
    }

    private String firstText(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.get(0).asText("").trim() : "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? (fallback == null ? "" : fallback) : preferred;
    }

    private String normalizeDoi(String doi) {
        return doi == null ? "" : doi.toLowerCase(Locale.ROOT)
                .replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .replace("doi:", "")
                .trim();
    }
}
