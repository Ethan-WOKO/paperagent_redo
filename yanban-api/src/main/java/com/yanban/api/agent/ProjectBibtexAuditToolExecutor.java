package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectFileResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.research.BibtexAuditItem;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.research.UntrustedResearchContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** First-version BibTeX audit: flat entries with brace-balanced, line-oriented fields only. */
@Component
public final class ProjectBibtexAuditToolExecutor extends AbstractResearchProjectToolExecutor {
    private static final String PARSER = "bibtex-audit@1";
    private static final Pattern ENTRY = Pattern.compile("^\\s*@([A-Za-z]+)\\s*\\{\\s*([^,\\s]+)\\s*,");
    private static final Pattern FIELD = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*=");
    private static final Pattern CITE = Pattern.compile("\\\\cite(?:[A-Za-z*]*)?(?:\\[[^]]*])?\\{([^}]+)}");

    public ProjectBibtexAuditToolExecutor(ProjectService projects, ObjectMapper objectMapper) { super("project_bibtex_audit", projects, objectMapper); }

    @Override protected ResearchToolOutcome analyze(ResearchContext context, JsonNode arguments) {
        Map<ProjectRelativePath, ProjectFileEntry> files = requestedFiles(context, arguments, false);
        List<BibtexAuditItem> items = new ArrayList<>(); List<ResearchEvidenceRef> evidence = new ArrayList<>();
        Map<String, Occurrence> keys = new HashMap<>(); Map<String, CitationOccurrence> cited = new HashMap<>(); long bytes = 0;
        boolean partial = files.size() < arguments.path("relativePaths").size(); boolean parseFailed = false;
        boolean supported = files.keySet().stream().map(path -> path.value().toLowerCase(Locale.ROOT))
                .anyMatch(path -> path.endsWith(".bib") || path.endsWith(".tex"));
        if (!supported) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.UNSUPPORTED_FILE_TYPE,
                "BibTeX audit received no .bib or .tex Project file");
        for (Map.Entry<ProjectRelativePath, ProjectFileEntry> entry : files.entrySet()) {
            if (bytes + entry.getValue().sizeBytes() > contract().budget().maxBytesInspected()) {
                // Field audits are normally finalized after all supplied material is read. If
                // the next file crosses budget, materialize already parsed entries first so a
                // real prior finding is preserved as TRUNCATED rather than becoming a false
                // first-input BUDGET_EXCEEDED.
                for (Occurrence occurrence : keys.values()) if (!occurrence.requiredFieldsPresent())
                    add(items, evidence, context, occurrence, "MISSING_REQUIRED_FIELD", occurrence.key(),
                            "title, author, and year are required in first-version audit");
                if (items.isEmpty()) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.BUDGET_EXCEEDED, "BibTeX input exceeds byte budget");
                return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
            ProjectFileResponse file = read(context, entry.getKey()); bytes += utf8Bytes(file);
            String lower = entry.getKey().value().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".bib")) parseFailed |= parseBib(context, entry, file.content(), keys, items, evidence);
            else if (lower.endsWith(".tex")) collectCitations(entry, file.content(), cited);
            else partial = true;
        }
        if (parseFailed) return ResearchToolSupport.parseFailed(usage(files.size(), 0, 0, bytes));
        for (Occurrence occurrence : keys.values()) {
            if (!occurrence.requiredFieldsPresent()) add(items, evidence, context, occurrence, "MISSING_REQUIRED_FIELD", occurrence.key(), "title, author, and year are required in first-version audit");
        }
        for (Occurrence occurrence : keys.values()) if (!cited.containsKey(occurrence.key()) && arguments.path("includeUnusedEntries").asBoolean(false))
            add(items, evidence, context, occurrence, "UNUSED_ENTRY", occurrence.key(), "entry is not cited by supplied LaTeX files");
        for (CitationOccurrence citation : cited.values()) if (!keys.containsKey(citation.key())) {
            Occurrence occurrence = new Occurrence(citation.key(), citation.path(), citation.file(), citation.line(), Set.of());
            add(items, evidence, context, occurrence, "MISSING_CITATION_KEY", citation.key(), "citation key is absent from supplied BibTeX files");
        }
        // add() is deliberately bounded.  Hitting the declared ceiling is observable rather
        // than silently treating later duplicate/field/citation issues as complete.
        return ResearchToolSupport.outcome(items, evidence, partial || parseFailed,
                items.size() >= contract().budget().maxOutputItems(), usage(files.size(), items.size(), evidence.size(), bytes));
    }

    private boolean parseBib(ResearchContext context, Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content,
                             Map<String, Occurrence> keys, List<BibtexAuditItem> items, List<ResearchEvidenceRef> evidence) {
        String[] lines = content.split("\\R", -1); Occurrence current = null; boolean malformed = false;
        for (int i = 0; i < lines.length; i++) {
            Matcher start = ENTRY.matcher(lines[i]);
            if (start.find()) {
                if (current != null) malformed = true;
                current = new Occurrence(start.group(2), entry.getKey(), entry.getValue(), i + 1, new HashSet<>());
                Occurrence previous = keys.putIfAbsent(current.key(), current);
                if (previous != null) add(items, evidence, context, current, "DUPLICATE_KEY", current.key(), "duplicate citation key");
                continue;
            }
            if (current != null) {
                Matcher field = FIELD.matcher(lines[i]); if (field.find()) current.fields().add(field.group(1).toLowerCase(Locale.ROOT));
                if (lines[i].trim().equals("}")) current = null;
            }
        }
        return malformed || current != null;
    }

    private void collectCitations(Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, String content,
                                  Map<String, CitationOccurrence> cited) {
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = CITE.matcher(lines[index]);
            while (matcher.find()) for (String key : matcher.group(1).split(",")) if (!key.isBlank())
                cited.putIfAbsent(key.trim(), new CitationOccurrence(key.trim(), entry.getKey(), entry.getValue(), index + 1));
        }
    }

    private void add(List<BibtexAuditItem> items, List<ResearchEvidenceRef> evidence, ResearchContext context, Occurrence occurrence,
                     String issue, String key, String detail) {
        if (items.size() >= contract().budget().maxOutputItems()) return;
        ResearchEvidenceRef ref = ResearchToolSupport.evidence(context, occurrence.path(), occurrence.file(), occurrence.line(), occurrence.line(), PARSER);
        items.add(new BibtexAuditItem(issue, key, detail, new UntrustedResearchContent(detail, ref))); evidence.add(ref);
    }

    private record Occurrence(String key, ProjectRelativePath path, ProjectFileEntry file, int line, Set<String> fields) {
        boolean requiredFieldsPresent() { return fields.containsAll(Set.of("title", "author", "year")); }
    }
    private record CitationOccurrence(String key, ProjectRelativePath path, ProjectFileEntry file, int line) { }
}
