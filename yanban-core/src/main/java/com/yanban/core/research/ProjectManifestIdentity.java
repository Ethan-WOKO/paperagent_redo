package com.yanban.core.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Deterministic, content-addressed identity for an immutable Project manifest. */
public final class ProjectManifestIdentity {
    private static final byte[] DOMAIN = "yanban-project-version-v1\0".getBytes(StandardCharsets.UTF_8);

    private ProjectManifestIdentity() { }

    public static ProjectVersionRef derive(List<Entry> entries) {
        if (entries == null) throw new IllegalArgumentException("project manifest must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            List<Entry> sorted = entries.stream().map(ProjectManifestIdentity::validated)
                    .sorted(Comparator.comparing(entry -> entry.relativePath().value())).toList();
            String previous = null;
            for (Entry entry : sorted) {
                if (entry.relativePath().value().equals(previous)) {
                    throw new IllegalArgumentException("project manifest contains duplicate paths");
                }
                previous = entry.relativePath().value();
                field(digest, entry.relativePath().value());
                field(digest, Long.toString(entry.sizeBytes()));
                field(digest, entry.fileHash().sha256());
            }
            return new ProjectVersionRef(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static Entry validated(Entry entry) {
        if (entry == null || entry.relativePath() == null || entry.fileHash() == null || entry.sizeBytes() < 0) {
            throw new IllegalArgumentException("manifest entry is incomplete");
        }
        return entry;
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) 0);
    }

    public record Entry(ProjectRelativePath relativePath, FileHash fileHash, long sizeBytes) { }
}
