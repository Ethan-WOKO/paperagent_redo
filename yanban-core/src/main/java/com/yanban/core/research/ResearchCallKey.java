package com.yanban.core.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Canonical, server-scoped repeat/deduplication key. Project identity and capability are never model inputs. */
public record ResearchCallKey(String toolName, ProjectVersionRef projectVersion, String argumentsSha256) {
    public ResearchCallKey {
        if (toolName == null || toolName.isBlank() || projectVersion == null
                || argumentsSha256 == null || !argumentsSha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("research call key is incomplete");
        }
    }

    public static ResearchCallKey of(String toolName, ProjectVersionRef projectVersion, JsonNode modelArguments) {
        if (modelArguments == null) {
            throw new IllegalArgumentException("model arguments must not be null");
        }
        return new ResearchCallKey(toolName, projectVersion, sha256(canonicalize(modelArguments).toString()));
    }

    public String value() {
        return toolName + ":" + projectVersion.value() + ":" + argumentsSha256;
    }

    public static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(fields::add);
            fields.stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.set(entry.getKey(), canonicalize(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node.deepCopy();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
