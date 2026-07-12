package com.yanban.core.research;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Fail closed: server authority must never be rendered as ordinary JSON. */
public final class ResearchRuntimeScopeSerializer extends JsonSerializer<ResearchRuntimeScope> {
    @Override
    public void serialize(ResearchRuntimeScope value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        throw JsonMappingException.from(generator, "ResearchRuntimeScope is server-only and cannot be serialized");
    }
}
