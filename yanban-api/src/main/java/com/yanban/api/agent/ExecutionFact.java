package com.yanban.api.agent;

import java.util.List;

/** Provider receipt facts. These fields attest execution and captured bytes, not semantic correctness. */
public record ExecutionFact(
        String provider,
        String status,
        Integer exitCode,
        boolean timedOut,
        List<String> command,
        String stdout,
        String stderr,
        String failurePhase,
        String failureType,
        String providerErrorType,
        Integer providerCommandExitCode
) {
    public ExecutionFact {
        command = command == null ? List.of() : List.copyOf(command);
    }

    public ExecutionFact(String provider, String status, Integer exitCode, boolean timedOut,
                         List<String> command, String stdout, String stderr) {
        this(provider, status, exitCode, timedOut, command, stdout, stderr, null, null, null, null);
    }
}
