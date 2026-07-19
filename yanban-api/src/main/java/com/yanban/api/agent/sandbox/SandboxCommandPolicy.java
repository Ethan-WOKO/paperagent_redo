package com.yanban.api.agent.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.yanban.sandbox.contract.SandboxCommandProfiles;

/** Server-owned executable and environment ceiling. No shell interpreters are admitted. */
@Component
public final class SandboxCommandPolicy {
    private static final Set<String> EXECUTABLES = Set.of("mvn", "java", "javac", "git");
    private static final Set<String> ENVIRONMENT = Set.of();

    public void validate(List<String> argv, Map<String, String> environment) {
        try { SandboxCommandProfiles.requireAllowed(argv); }
        catch (IllegalArgumentException ex) { reject(SandboxFailureCode.COMMAND_NOT_ALLOWED, "command profile is not server-allowlisted"); }
        Map<String, String> safeEnvironment = environment == null ? Map.of() : environment;
        if (!ENVIRONMENT.containsAll(safeEnvironment.keySet()) || safeEnvironment.entrySet().stream()
                .anyMatch(entry -> invalidArgument(entry.getValue()) || secretName(entry.getKey())))
            reject(SandboxFailureCode.ENV_NOT_ALLOWED, "environment is not server-allowlisted");
    }

    private boolean matchesProfile(List<String> argv) {
        return switch (argv.get(0)) {
            case "mvn" -> maven(argv);
            case "java" -> argv.equals(List.of("java", "-version"));
            case "javac" -> argv.size() >= 2 && argv.size() <= 33
                    && argv.subList(1, argv.size()).stream().allMatch(this::safeJavaSource);
            case "git" -> argv.equals(List.of("git", "diff", "--check"))
                    || argv.equals(List.of("git", "status", "--short"))
                    || argv.equals(List.of("git", "rev-parse", "--verify", "HEAD"));
            default -> false;
        };
    }

    private boolean maven(List<String> argv) {
        if (argv.size() < 2 || argv.size() > 8) return false;
        boolean goal = false;
        for (int i = 1; i < argv.size(); i++) {
            String arg = argv.get(i);
            if ("test".equals(arg) || "verify".equals(arg)) { if (goal) return false; goal = true; continue; }
            if (Set.of("-o", "-q", "-am").contains(arg)) continue;
            if ("-pl".equals(arg) && i + 1 < argv.size() && argv.get(++i).matches("[A-Za-z0-9_.-]+(,[A-Za-z0-9_.-]+)*")) continue;
            return false;
        }
        return goal;
    }

    private boolean safeJavaSource(String value) {
        try {
            var path = java.nio.file.Path.of(value);
            return !path.isAbsolute() && path.normalize().equals(path) && value.endsWith(".java")
                    && !value.contains("\\") && !value.startsWith(".");
        } catch (RuntimeException ex) { return false; }
    }

    private boolean invalidArgument(String value) {
        return value == null || value.isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0
                || value.contains("\r") || value.contains("\n");
    }

    private boolean secretName(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("TOKEN") || upper.contains("SECRET") || upper.contains("PASSWORD") || upper.contains("KEY");
    }

    private static void reject(SandboxFailureCode code, String message) {
        throw new SandboxExecutionException(code, message);
    }
}
