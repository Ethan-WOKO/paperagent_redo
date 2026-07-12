package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectFileResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.research.LatexOutlineItem;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.research.UntrustedResearchContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative line-oriented LaTeX outline parser; it deliberately does not expand includes. */
@Component
public final class ProjectLatexOutlineToolExecutor extends AbstractResearchProjectToolExecutor {
    private static final String PARSER = "latex-outline@1";
    private static final Pattern SECTION = Pattern.compile("\\\\(part|chapter|section|subsection|subsubsection)\\*?\\{([^}]*)}");
    private static final Pattern LABEL = Pattern.compile("\\\\label\\{([^}]+)}");
    private static final Pattern REF = Pattern.compile("\\\\(?:ref|eqref)\\{([^}]+)}");

    public ProjectLatexOutlineToolExecutor(ProjectService projects, ObjectMapper objectMapper) {
        super("project_latex_outline", projects, objectMapper);
    }

    @Override protected ResearchToolOutcome analyze(ResearchContext context, JsonNode arguments) {
        Map<ProjectRelativePath, ProjectFileEntry> files = requestedFiles(context, arguments, false);
        List<LatexOutlineItem> items = new ArrayList<>(); List<ResearchEvidenceRef> evidence = new ArrayList<>();
        long bytes = 0; boolean partial = files.size() < arguments.path("relativePaths").size();
        boolean includeRefs = arguments.path("includeFormulaReferences").asBoolean(false);
        boolean supported = files.keySet().stream().anyMatch(path -> path.value().toLowerCase(java.util.Locale.ROOT).endsWith(".tex"));
        if (!supported) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.UNSUPPORTED_FILE_TYPE,
                "LaTeX outline received no .tex Project file");
        boolean malformed = false;
        for (Map.Entry<ProjectRelativePath, ProjectFileEntry> entry : files.entrySet()) {
            if (!entry.getKey().value().toLowerCase(java.util.Locale.ROOT).endsWith(".tex")) { partial = true; continue; }
            if (bytes + entry.getValue().sizeBytes() > contract().budget().maxBytesInspected()) {
                if (items.isEmpty()) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.BUDGET_EXCEEDED, "LaTeX input exceeds byte budget");
                return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
            ProjectFileResponse file = read(context, entry.getKey()); bytes += utf8Bytes(file);
            String[] lines = file.content().split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if ((lines[i].contains("\\section{") || lines[i].contains("\\label{") || lines[i].contains("\\ref{"))
                        && !lines[i].contains("}")) malformed = true;
                Matcher section = SECTION.matcher(lines[i]); Matcher label = LABEL.matcher(lines[i]); Matcher ref = REF.matcher(lines[i]);
                if (section.find()) add(items, evidence, context, entry, i, "SECTION", section.group(1), section.group(2), lines[i]);
                if (label.find()) add(items, evidence, context, entry, i, "LABEL", label.group(1), "label", lines[i]);
                if (includeRefs && ref.find()) add(items, evidence, context, entry, i, "FORMULA_REFERENCE", ref.group(1), "reference", lines[i]);
                if (items.size() >= contract().budget().maxOutputItems())
                    return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
        }
        if (malformed && items.isEmpty()) return ResearchToolSupport.parseFailed(usage(files.size(), 0, 0, bytes));
        return ResearchToolSupport.outcome(items, evidence, partial || malformed, false, usage(files.size(), items.size(), evidence.size(), bytes));
    }

    private void add(List<LatexOutlineItem> items, List<ResearchEvidenceRef> evidence, ResearchContext context,
                     Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, int index, String kind, String identifier, String detail, String source) {
        ResearchEvidenceRef ref = ResearchToolSupport.evidence(context, entry.getKey(), entry.getValue(), index + 1, index + 1, PARSER);
        items.add(new LatexOutlineItem(kind, identifier, detail, new UntrustedResearchContent(source, ref)));
        evidence.add(ref);
    }
}
