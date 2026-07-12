package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectFileResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.research.CodeSymbolItem;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.research.UntrustedResearchContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative Java/Python/MATLAB symbol extractor; it never infers control flow or types. */
@Component
public final class ProjectCodeSymbolsToolExecutor extends AbstractResearchProjectToolExecutor {
    private static final String PARSER = "code-symbols@1";
    private static final Pattern JAVA = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)|(?:public|protected|private|static|final|\\s)+\\s*[A-Za-z_$][A-Za-z0-9_$<>\\[\\]]*\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern PYTHON = Pattern.compile("^\\s*(class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern MATLAB = Pattern.compile("^\\s*function(?:\\s+[^=]+\\s*=)?\\s*([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern DEPENDENCY = Pattern.compile("^\\s*(?:import|from)\\s+([^;#\\s]+)");

    public ProjectCodeSymbolsToolExecutor(ProjectService projects, ObjectMapper objectMapper) { super("project_code_symbols", projects, objectMapper); }

    @Override protected ResearchToolOutcome analyze(ResearchContext context, JsonNode arguments) {
        Map<ProjectRelativePath, ProjectFileEntry> files = requestedFiles(context, arguments, false);
        List<CodeSymbolItem> items = new ArrayList<>(); List<ResearchEvidenceRef> evidence = new ArrayList<>(); long bytes = 0;
        boolean partial = files.size() < arguments.path("relativePaths").size(); String query = arguments.path("symbolQuery").asText("");
        boolean dependencies = arguments.path("includeDependencies").asBoolean(false);
        boolean supported = files.keySet().stream().map(path -> extension(path.value()))
                .anyMatch(extension -> extension.equals("java") || extension.equals("py") || extension.equals("m"));
        if (!supported) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.UNSUPPORTED_FILE_TYPE,
                "code symbol extraction received no supported Java, Python, or MATLAB file");
        for (Map.Entry<ProjectRelativePath, ProjectFileEntry> entry : files.entrySet()) {
            String extension = extension(entry.getKey().value());
            if (!(extension.equals("java") || extension.equals("py") || extension.equals("m"))) { partial = true; continue; }
            if (bytes + entry.getValue().sizeBytes() > contract().budget().maxBytesInspected()) {
                if (items.isEmpty()) throw new com.yanban.core.research.ResearchContractException(ResearchToolErrorCode.BUDGET_EXCEEDED, "code input exceeds byte budget");
                return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
            ProjectFileResponse file = read(context, entry.getKey()); bytes += utf8Bytes(file); String[] lines = file.content().split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String name = symbol(extension, lines[i]);
                if (name != null && (query.isBlank() || name.contains(query))) add(items, evidence, context, entry, i, "SYMBOL", name, null, lines[i]);
                if (dependencies) { Matcher dependency = DEPENDENCY.matcher(lines[i]); if (dependency.find()) add(items, evidence, context, entry, i, "DEPENDENCY", dependency.group(1), dependency.group(1), lines[i]); }
                if (items.size() >= contract().budget().maxOutputItems()) return ResearchToolSupport.outcome(items, evidence, partial, true, usage(files.size(), items.size(), evidence.size(), bytes));
            }
        }
        return ResearchToolSupport.outcome(items, evidence, partial, false, usage(files.size(), items.size(), evidence.size(), bytes));
    }

    private String symbol(String extension, String line) {
        Matcher match = switch (extension) { case "java" -> JAVA.matcher(line); case "py" -> PYTHON.matcher(line); default -> MATLAB.matcher(line); };
        if (!match.find()) return null;
        if (extension.equals("java")) return match.group(2) != null ? match.group(2) : match.group(3);
        return extension.equals("m") ? match.group(1) : match.group(2);
    }

    private void add(List<CodeSymbolItem> items, List<ResearchEvidenceRef> evidence, ResearchContext context,
                     Map.Entry<ProjectRelativePath, ProjectFileEntry> entry, int index, String kind, String name, String dependency, String source) {
        if (name == null || name.isBlank()) return;
        ResearchEvidenceRef ref = ResearchToolSupport.evidence(context, entry.getKey(), entry.getValue(), index + 1, index + 1, PARSER);
        items.add(new CodeSymbolItem(kind, name, dependency, new UntrustedResearchContent(source, ref))); evidence.add(ref);
    }

    private String extension(String path) { int index = path.lastIndexOf('.'); return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT); }
}
