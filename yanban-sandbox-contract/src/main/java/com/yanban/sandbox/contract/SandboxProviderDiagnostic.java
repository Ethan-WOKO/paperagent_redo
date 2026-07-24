package com.yanban.sandbox.contract;

/** Safe provider failure diagnostics. Free-form provider messages are intentionally excluded. */
public record SandboxProviderDiagnostic(
        String failurePhase,
        String failureType,
        String providerErrorType,
        Integer providerCommandExitCode
) {
}
