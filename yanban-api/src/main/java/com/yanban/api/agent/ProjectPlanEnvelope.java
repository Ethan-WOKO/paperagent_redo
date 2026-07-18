package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Versioned server-owned wrapper around an otherwise untrusted planner raw JSON payload. */
final class ProjectPlanEnvelope {
    private static final String SCHEMA_V1 = "project_plan_envelope_v1";
    private static final String SCHEMA_V2 = "project_plan_envelope_v2";
    private ProjectPlanEnvelope() { }

    static String wrap(ObjectMapper json, String plannerRawJson, ProjectRuntimeContext context) {
        return wrap(json, plannerRawJson, context, null);
    }

    static String wrapControlled(ObjectMapper json, String plannerRawJson, ProjectRuntimeContext context,
                                 JsonNode controlledPlanEnvelope) {
        if (context == null || controlledPlanEnvelope == null || !controlledPlanEnvelope.isObject()) {
            throw new IllegalArgumentException("controlled Project Plan envelope requires Project context");
        }
        return wrap(json, plannerRawJson, context, controlledPlanEnvelope);
    }

    private static String wrap(ObjectMapper json, String plannerRawJson, ProjectRuntimeContext context,
                               JsonNode controlledPlanEnvelope) {
        ObjectNode root = json.createObjectNode();
        root.put("schemaVersion", controlledPlanEnvelope == null ? SCHEMA_V1 : SCHEMA_V2);
        root.put("plannerRawJson", plannerRawJson == null ? "" : plannerRawJson);
        if (context == null) {
            root.putNull("serverAttestedProjectContext");
        } else {
            ObjectNode trusted = root.putObject("serverAttestedProjectContext");
            trusted.put("projectId", context.projectId());
            trusted.put("capability", "PROJECT_READ");
        }
        if (controlledPlanEnvelope != null) {
            root.set("controlledPlanEnvelope", controlledPlanEnvelope.deepCopy());
        }
        return write(json, root);
    }

    static ProjectRuntimeContext restore(ObjectMapper json, String raw, Long userId) {
        if (raw == null || userId == null) return null;
        try {
            JsonNode root = json.readTree(raw);
            if (root == null || !root.isObject()) return null; // legacy planner JSON is never trusted.
            boolean claimsEnvelope = root.has("schemaVersion") || root.has("serverAttestedProjectContext") || root.has("plannerRawJson");
            if (!claimsEnvelope) return null;
            String schema = root.path("schemaVersion").asText();
            boolean v1 = SCHEMA_V1.equals(schema);
            boolean v2 = SCHEMA_V2.equals(schema);
            if ((!v1 && !v2) || root.size() != (v2 ? 4 : 3)
                    || !root.path("plannerRawJson").isTextual() || !root.has("serverAttestedProjectContext")) {
                throw new IllegalStateException("Invalid server-owned Project Plan envelope");
            }
            if (v2 && !root.path("controlledPlanEnvelope").isObject()) {
                throw new IllegalStateException("Invalid controlled Project Plan envelope");
            }
            JsonNode trusted = root.get("serverAttestedProjectContext");
            if (trusted == null || trusted.isNull()) return null;
            if (!trusted.isObject() || trusted.size() != 2 || !trusted.path("projectId").canConvertToLong()
                    || trusted.path("projectId").longValue() <= 0 || !"PROJECT_READ".equals(trusted.path("capability").asText())) {
                throw new IllegalStateException("Invalid server-attested Project Plan context");
            }
            return new ProjectRuntimeContext(userId, trusted.path("projectId").longValue());
        } catch (IllegalStateException ex) { throw ex;
        } catch (Exception ex) { throw new IllegalStateException("Invalid Project Plan envelope", ex); }
    }

    static JsonNode restoreControlled(ObjectMapper json, String raw, Long userId) {
        ProjectRuntimeContext context = restore(json, raw, userId);
        if (context == null) return null;
        try {
            JsonNode root = json.readTree(raw);
            if (!SCHEMA_V2.equals(root.path("schemaVersion").asText())) return null;
            return root.path("controlledPlanEnvelope").deepCopy();
        } catch (IllegalStateException ex) { throw ex;
        } catch (Exception ex) { throw new IllegalStateException("Invalid controlled Project Plan envelope", ex); }
    }

    private static String write(ObjectMapper json, ObjectNode root) {
        try { return json.writeValueAsString(root); }
        catch (Exception ex) { throw new IllegalStateException("Cannot persist Project plan envelope", ex); }
    }
}
