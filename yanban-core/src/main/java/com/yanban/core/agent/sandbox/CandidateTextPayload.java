package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yanban.core.research.FileHash;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable full-replacement UTF-8 text payload. Binary and streaming patches are deliberately deferred. */
public final class CandidateTextPayload implements RejectsUnknownFields {
    public static final int MAX_UTF8_BYTES = 4 * 1024 * 1024;

    private final String text;
    private final int utf8Bytes;
    private final FileHash contentHash;

    private CandidateTextPayload(String text) {
        if (text == null) throw new IllegalArgumentException("candidate text must not be null");
        byte[] encoded = encode(text);
        if (encoded.length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("candidate text exceeds the absolute contract byte limit");
        }
        this.text = text;
        this.utf8Bytes = encoded.length;
        this.contentHash = hash(encoded);
    }

    public static CandidateTextPayload fromText(String text) {
        return new CandidateTextPayload(text);
    }

    @JsonCreator
    public static CandidateTextPayload fromJson(
            @JsonProperty(value = "text", required = true) String text,
            @JsonProperty(value = "utf8Bytes", required = true) Integer utf8Bytes,
            @JsonProperty(value = "contentHash", required = true) FileHash contentHash) {
        if (utf8Bytes == null || contentHash == null) {
            throw new IllegalArgumentException("serialized candidate text is missing its deterministic identity");
        }
        CandidateTextPayload payload = new CandidateTextPayload(text);
        if (payload.utf8Bytes != utf8Bytes || !payload.contentHash.equals(contentHash)) {
            throw new IllegalArgumentException("serialized candidate text identity does not match its UTF-8 content");
        }
        return payload;
    }

    @JsonProperty("text")
    public String text() { return text; }

    @JsonProperty("utf8Bytes")
    public int utf8Bytes() { return utf8Bytes; }

    @JsonProperty("contentHash")
    public FileHash contentHash() { return contentHash; }

    static byte[] encode(String value) {
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] encoded = new byte[bytes.remaining()];
            bytes.get(encoded);
            return encoded;
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("candidate text is not valid Unicode", ex);
        }
    }

    static FileHash hash(byte[] encoded) {
        try {
            return new FileHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateTextPayload that)) return false;
        return utf8Bytes == that.utf8Bytes && text.equals(that.text) && contentHash.equals(that.contentHash);
    }

    @Override
    public int hashCode() { return Objects.hash(text, utf8Bytes, contentHash); }
}
