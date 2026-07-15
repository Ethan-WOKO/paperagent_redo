package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Fail closed: a validation capability is runtime authority, not Candidate JSON. */
public final class CandidateValidationDecisionSerializer extends JsonSerializer<CandidateValidationDecision> {
    @Override
    public void serialize(CandidateValidationDecision value, JsonGenerator generator,
                          SerializerProvider serializers) throws IOException {
        throw JsonMappingException.from(generator, "CandidateValidationDecision is server-only and cannot be serialized");
    }
}
