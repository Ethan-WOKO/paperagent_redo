package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanban.sandbox.contract.SandboxProviderDiagnostic;
import org.junit.jupiter.api.Test;

class SandboxWorkerProviderDiagnosticTest {

    @Test
    void extractsOnlyProviderExceptionTypeWithoutLoggingProviderMessage() {
        assertThat(SandboxWorker.providerErrorType(
                "E2B provider error: TimeoutError: sensitive provider detail"))
                .isEqualTo("TimeoutError");
        assertThat(SandboxWorker.providerErrorType("arbitrary stderr body"))
                .isEqualTo("UNCLASSIFIED");
        assertThat(SandboxWorker.providerErrorType(""))
                .isEqualTo("UNREPORTED");
    }

    @Test
    void recordsSafeFailurePhaseAndProviderCommandExitCodeWithoutMessage() {
        SandboxProviderDiagnostic diagnostic = SandboxWorker.diagnostic(
                "CREATE",
                new IllegalStateException("sensitive provider message"),
                "TimeoutError",
                70);

        assertThat(diagnostic.failurePhase()).isEqualTo("CREATE");
        assertThat(diagnostic.failureType()).isEqualTo("IllegalStateException");
        assertThat(diagnostic.providerErrorType()).isEqualTo("TimeoutError");
        assertThat(diagnostic.providerCommandExitCode()).isEqualTo(70);
        assertThat(diagnostic.toString()).doesNotContain("sensitive provider message");
    }

    @Test
    void recordsUnexpectedBrokerStatusWithoutPersistingExceptionMessage() {
        IllegalStateException failure = new IllegalStateException("sensitive runtime detail");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.yanban.sandboxbroker.SandboxWorker", "runClaim", "SandboxWorker.java", 82)
        });

        SandboxProviderDiagnostic diagnostic = SandboxWorker.unexpectedDiagnostic("RUNNING", failure);

        assertThat(diagnostic.failurePhase()).isEqualTo("BROKER_RUNNING");
        assertThat(diagnostic.failureType()).isEqualTo("IllegalStateException");
        assertThat(diagnostic.providerErrorType()).isEqualTo("UNEXPECTED_RUNTIME");
        assertThat(diagnostic.providerCommandExitCode()).isNull();
        assertThat(diagnostic.toString()).doesNotContain("sensitive runtime detail");
        assertThat(SandboxWorker.failureLocation(failure))
                .isEqualTo("com.yanban.sandboxbroker.SandboxWorker#runClaim:82");
    }
}
