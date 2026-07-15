package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Fail closed: server snapshot attestation is runtime authority and cannot enter JSON. */
public final class SandboxSnapshotAttestationSerializer extends JsonSerializer<SandboxSnapshotAttestation> {
    @Override
    public void serialize(SandboxSnapshotAttestation value, JsonGenerator generator,
                          SerializerProvider serializers) throws IOException {
        throw JsonMappingException.from(generator, "SandboxSnapshotAttestation is server-only and cannot be serialized");
    }
}
