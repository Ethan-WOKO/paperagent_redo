package com.yanban.core.research;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;

/** Model-controlled input rules. Runtime-attested project/user/capability fields do not belong here. */
public record ResearchToolInputPolicy(Set<String> requiredFields, Set<String> allowedFields,
                                      Map<String, Integer> maxArrayItems, Map<String, Integer> maxArrayItemLengths,
                                      Map<String, Integer> maxStringLengths,
                                      Set<String> relativePathArrayFields, Set<String> booleanFields,
                                      Map<String, IntegerRange> integerFields) {
    public ResearchToolInputPolicy {
        requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        allowedFields = allowedFields == null ? Set.of() : Set.copyOf(allowedFields);
        maxArrayItems = maxArrayItems == null ? Map.of() : Map.copyOf(maxArrayItems);
        maxArrayItemLengths = maxArrayItemLengths == null ? Map.of() : Map.copyOf(maxArrayItemLengths);
        maxStringLengths = maxStringLengths == null ? Map.of() : Map.copyOf(maxStringLengths);
        relativePathArrayFields = relativePathArrayFields == null ? Set.of() : Set.copyOf(relativePathArrayFields);
        booleanFields = booleanFields == null ? Set.of() : Set.copyOf(booleanFields);
        integerFields = integerFields == null ? Map.of() : Map.copyOf(integerFields);
        if (!allowedFields.containsAll(requiredFields)) {
            throw new IllegalArgumentException("required fields must be allowed");
        }
        if (!maxArrayItems.keySet().equals(maxArrayItemLengths.keySet())) {
            throw new IllegalArgumentException("every array field must define an item length limit");
        }
    }

    public void validate(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw invalid("arguments must be a JSON object");
        }
        for (String required : requiredFields) {
            if (!arguments.has(required) || arguments.get(required).isNull()) {
                throw invalid("missing required field: " + required);
            }
            if (maxArrayItems.containsKey(required) && arguments.get(required).isArray() && arguments.get(required).size() == 0) {
                throw invalid("required array field must not be empty: " + required);
            }
        }
        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw invalid("unsupported model field: " + field);
            }
        });
        for (Map.Entry<String, Integer> entry : maxStringLengths.entrySet()) {
            JsonNode value = arguments.get(entry.getKey());
            if (value != null && (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > entry.getValue())) {
                throw invalid("invalid string field: " + entry.getKey());
            }
        }
        for (Map.Entry<String, Integer> entry : maxArrayItems.entrySet()) {
            JsonNode value = arguments.get(entry.getKey());
            if (value != null && (!value.isArray() || value.size() > entry.getValue())) {
                throw invalid("invalid array field: " + entry.getKey());
            }
            if (value != null) {
                for (JsonNode item : value) {
                    if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > maxArrayItemLengths.get(entry.getKey())) {
                        throw invalid("invalid array item: " + entry.getKey());
                    }
                }
            }
        }
        for (String field : relativePathArrayFields) {
            JsonNode values = arguments.get(field);
            if (values != null) {
                for (JsonNode value : values) {
                    if (!value.isTextual()) {
                        throw invalid("relative path must be a string");
                    }
                    try {
                        ProjectRelativePath.of(value.textValue());
                    } catch (IllegalArgumentException exception) {
                        throw new ResearchContractException(ResearchToolErrorCode.PATH_OUTSIDE_PROJECT, exception.getMessage());
                    }
                }
            }
        }
        for (String field : booleanFields) {
            JsonNode value = arguments.get(field);
            if (value != null && !value.isBoolean()) {
                throw invalid("invalid boolean field: " + field);
            }
        }
        for (Map.Entry<String, IntegerRange> entry : integerFields.entrySet()) {
            JsonNode value = arguments.get(entry.getKey());
            if (value != null && (!value.isIntegralNumber() || !value.canConvertToInt() || !entry.getValue().contains(value.intValue()))) {
                throw invalid("invalid integer field: " + entry.getKey());
            }
        }
    }

    private static ResearchContractException invalid(String message) {
        return new ResearchContractException(ResearchToolErrorCode.INVALID_ARGUMENT, message);
    }

    public record IntegerRange(int min, int max) {
        public IntegerRange {
            if (min > max) {
                throw new IllegalArgumentException("integer range must be ordered");
            }
        }

        boolean contains(int value) {
            return value >= min && value <= max;
        }
    }
}
