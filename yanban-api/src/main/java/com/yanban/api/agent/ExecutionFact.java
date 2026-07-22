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
        String stderr
) {
    public ExecutionFact {
        command = command == null ? List.of() : List.copyOf(command);
    }
}
