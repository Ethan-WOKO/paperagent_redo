package com.yanban.api.agent.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SandboxExecutionException extends RuntimeException {
    private final SandboxFailureCode code;
    private final String phase;
    private final Set<String> requestedPaths;
    private final Set<String> resolvedPaths;
    private final Set<String> missingPaths;
    private final Map<String, List<String>> ambiguities;

    public SandboxExecutionException(SandboxFailureCode code, String message) {
        this(code, message, null, Set.of(), Set.of(), Set.of(), Map.of(), null);
    }

    public SandboxExecutionException(SandboxFailureCode code, String message, Throwable cause) {
        this(code, message, null, Set.of(), Set.of(), Set.of(), Map.of(), cause);
    }

    public SandboxExecutionException(SandboxFailureCode code,
                                     String message,
                                     String phase,
                                     Set<String> requestedPaths,
                                     Set<String> resolvedPaths,
                                     Set<String> missingPaths,
                                     Map<String, List<String>> ambiguities) {
        this(code, message, phase, requestedPaths, resolvedPaths, missingPaths, ambiguities, null);
    }

    private SandboxExecutionException(SandboxFailureCode code,
                                      String message,
                                      String phase,
                                      Set<String> requestedPaths,
                                      Set<String> resolvedPaths,
                                      Set<String> missingPaths,
                                      Map<String, List<String>> ambiguities,
                                      Throwable cause) {
        super(message, cause);
        this.code = code;
        this.phase = phase;
        this.requestedPaths = requestedPaths == null ? Set.of() : Set.copyOf(requestedPaths);
        this.resolvedPaths = resolvedPaths == null ? Set.of() : Set.copyOf(resolvedPaths);
        this.missingPaths = missingPaths == null ? Set.of() : Set.copyOf(missingPaths);
        this.ambiguities = ambiguities == null ? Map.of() : Map.copyOf(ambiguities);
    }

    public SandboxFailureCode code() { return code; }
    public String phase() { return phase; }
    public Set<String> requestedPaths() { return requestedPaths; }
    public Set<String> resolvedPaths() { return resolvedPaths; }
    public Set<String> missingPaths() { return missingPaths; }
    public Map<String, List<String>> ambiguities() { return ambiguities; }

    public SandboxExecutionException withPathDiagnostic(String diagnosticPhase,
                                                        Set<String> requested,
                                                        Set<String> resolved,
                                                        Set<String> missing,
                                                        Map<String, List<String>> ambiguous) {
        if (phase != null) return this;
        return new SandboxExecutionException(
                code, getMessage(), diagnosticPhase, requested, resolved, missing, ambiguous, this);
    }
}
