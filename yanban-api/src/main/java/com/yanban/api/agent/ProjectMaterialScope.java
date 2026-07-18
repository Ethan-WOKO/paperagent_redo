package com.yanban.api.agent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server-owned extraction and comparison of explicitly named Project file materials. */
final class ProjectMaterialScope {

    static final String MISSING_TARGET_PREFIX = "TARGET_PROJECT_FILE_MISSING:";

    private static final Pattern RELATIVE_FILE = Pattern.compile(
            "(?i)([A-Za-z0-9_.()\\-]+(?:[/\\\\][A-Za-z0-9_.()\\-]+)*\\."
                    + "(?:tex|bib|py|java|kt|kts|js|jsx|ts|tsx|json|ya?ml|toml|xml|csv|tsv|"
                    + "md|rst|ipynb|mat|m|c|cc|cpp|cxx|h|hpp|sh|ps1))");

    private ProjectMaterialScope() {
    }

    static Set<String> explicitRelativePaths(String... values) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (values == null) return Set.of();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            Matcher matcher = RELATIVE_FILE.matcher(value);
            while (matcher.find()) {
                paths.add(normalize(matcher.group(1)));
            }
        }
        return Set.copyOf(paths);
    }

    static boolean contains(Set<String> scope, String path) {
        return scope == null || scope.isEmpty() || scope.contains(normalize(path));
    }

    static boolean hasDeterministicMissingTarget(AgentRuntimeResult result) {
        if (result == null || result.fallbacks() == null) return false;
        return result.fallbacks().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .anyMatch(value -> value.startsWith(MISSING_TARGET_PREFIX));
    }

    static String normalize(String path) {
        if (path == null) return "";
        return path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
