package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectFileResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.research.ExperimentSummaryItem;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.research.UntrustedResearchContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Bounded observation-only summaries. CSV values are reported, never aggregated or invented. */
@Component
public final class ProjectExperimentSummaryToolExecutor extends AbstractResearchProjectToolExecutor {
    private static final String PARSER = "experiment-summary@1";
    private static final Set<String> SUPPORTED = Set.of("csv", "json", "yaml", "yml", "txt", "md", "log");
    private static final Pattern SIMPLE_YAML = Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*)\\s*:\\s*(\\S.*)$");

    public ProjectExperimentSummaryToolExecutor(ProjectService projects, ObjectMapper objectMapper) { super("project_experiment_summary", projects, objectMapper); }

    @Override protected ResearchToolOutcome analyze(ResearchContext context, JsonNode arguments) {
        Map<ProjectRelativePath, ProjectFileEntry> files = requestedFiles(context, arguments, false);
        List<ExperimentSummaryItem> items = new ArrayList<>(); List<ResearchEvidenceRef> evidence = new ArrayList<>(); long bytes = 0;
        boolean partial = files.size() < arguments.path("relativePaths").size(); boolean parseFailed = false; boolean supported = false;
        int maxRows = arguments.path("maxRowsPerFile").asInt(100);
        Set<String> metrics = new HashSet<>(); arguments.path("metricNames").forEach(node -> metrics.add(node.asText()));
        for (Map.Entry<ProjectRelativePath, ProjectFileEntry> entry : files.entrySet()) {
            String extension = extension(entry.getKey().value());
            if (!SUPPORTED.contains(extension)) { partial = true; continue; }
            supported = true;
            if (bytes + entry.getValue().sizeBytes() > contract().budget().maxBytesInspected()) {
                if (items.isEmpty()) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.BUDGET_EXCEEDED, "experiment input exceeds byte budget");
                return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
            ProjectFileResponse file = read(context, entry.getKey()); bytes += utf8Bytes(file);
            boolean reliable;
            if (extension.equals("csv")) reliable = summaryCsv(context, entry, file.content(), metrics, maxRows, items, evidence);
            else if (extension.equals("json")) reliable = summaryJson(context, entry, file.content(), metrics, items, evidence);
            else if (extension.equals("yaml") || extension.equals("yml")) reliable = summaryYaml(context, entry, file.content(), metrics, items, evidence);
            else reliable = summaryText(context, entry, file.content(), extension, metrics, maxRows, items, evidence);
            if (!reliable) parseFailed = true;
            if (items.size() >= contract().budget().maxOutputItems()) return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
        }
        if (!supported) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.UNSUPPORTED_FILE_TYPE,
                "experiment summary received no supported Project file type");
        if (parseFailed && items.isEmpty()) return ResearchToolSupport.parseFailed(usage(files.size(), 0, 0, bytes));
        return ResearchToolSupport.outcome(items, evidence, partial || parseFailed, false, usage(files.size(), items.size(), evidence.size(), bytes));
    }

    private boolean summaryCsv(ResearchContext context, Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content,
                            Set<String> metrics, int maxRows, List<ExperimentSummaryItem> items, List<ResearchEvidenceRef> evidence) {
        String[] lines = content.split("\\R", -1); if (lines.length == 0 || lines[0].isBlank() || lines[0].contains("\"")) return false;
        String[] headers = lines[0].split(",", -1); int rows = Math.min(maxRows, Math.max(0, lines.length - 1));
        Set<String> distinct = new HashSet<>();
        for (String header : headers) if (header.isBlank() || !distinct.add(header.trim())) return false;
        for (int row = 1; row <= rows; row++) if (lines[row].contains("\"") || lines[row].split(",", -1).length != headers.length) return false;
        for (int column = 0; column < headers.length && items.size() < contract().budget().maxOutputItems(); column++) {
            String metric = headers[column].trim(); if (!metrics.isEmpty() && !metrics.contains(metric)) continue;
            String value = "observedRows=" + rows;
            if (rows > 0) { String[] cells = lines[rows].split(",", -1); if (column < cells.length) value += "; lastObserved=" + cells[column].trim(); }
            add(items, evidence, context, entry, 1, "CSV_METRIC", metric, value, lines[0]);
        }
        return true;
    }

    private boolean summaryJson(ResearchContext context, Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content,
                                Set<String> metrics, List<ExperimentSummaryItem> items, List<ResearchEvidenceRef> evidence) {
        try {
            JsonNode root = objectMapper.readTree(content); if (root == null || !root.isObject()) return false;
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields(); boolean found = false;
            while (fields.hasNext() && items.size() < contract().budget().maxOutputItems()) {
                Map.Entry<String, JsonNode> field = fields.next(); if (!metrics.isEmpty() && !metrics.contains(field.getKey())) continue;
                if (field.getValue().isContainerNode()) continue;
                add(items, evidence, context, entry, 1, "JSON_METRIC", field.getKey(), field.getValue().asText(), field.getKey() + ": " + field.getValue().asText()); found = true;
            }
            return found || metrics.isEmpty();
        } catch (Exception exception) { return false; }
    }

    private boolean summaryYaml(ResearchContext context, Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content,
                                Set<String> metrics, List<ExperimentSummaryItem> items, List<ResearchEvidenceRef> evidence) {
        boolean found = false; String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank() || lines[index].trim().startsWith("#")) continue;
            Matcher matcher = SIMPLE_YAML.matcher(lines[index]); if (!matcher.matches()) return false;
            String key = matcher.group(1); if (!metrics.isEmpty() && !metrics.contains(key)) continue;
            add(items, evidence, context, entry, index + 1, "YAML_METRIC", key, matcher.group(2).trim(), lines[index]); found = true;
            if (items.size() >= contract().budget().maxOutputItems()) break;
        }
        return found || metrics.isEmpty();
    }

    private boolean summaryText(ResearchContext context, Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content, String extension,
                                Set<String> metrics, int maxRows, List<ExperimentSummaryItem> items, List<ResearchEvidenceRef> evidence) {
        if (!metrics.isEmpty()) return false;
        String[] lines = content.split("\\R", -1); int limit = Math.min(maxRows, lines.length);
        for (int i = 0; i < limit && items.size() < contract().budget().maxOutputItems(); i++) if (!lines[i].isBlank()) {
            add(items, evidence, context, entry, i + 1, "REPORT", null,
                    lines[i].trim(), lines[i]); break;
        }
        return true;
    }

    private void add(List<ExperimentSummaryItem> items, List<ResearchEvidenceRef> evidence, ResearchContext context,
                     Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, int line, String type, String metric, String value, String source) {
        ResearchEvidenceRef ref = ResearchToolSupport.evidence(context, entry.getKey(), entry.getValue(), line, line, PARSER);
        items.add(new ExperimentSummaryItem(type, metric, value, new UntrustedResearchContent(source, ref))); evidence.add(ref);
    }

    private String extension(String path) { int index = path.lastIndexOf('.'); return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT); }
}
