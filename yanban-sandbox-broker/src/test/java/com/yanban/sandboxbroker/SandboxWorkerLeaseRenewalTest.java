package com.yanban.sandboxbroker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class SandboxWorkerLeaseRenewalTest {

    @Test
    void renewsLeaseBeforeEveryProviderProcess(@TempDir Path workspace) {
        SandboxLeaseService leases = mock(SandboxLeaseService.class);
        SandboxExecutionEntity entity = mock(SandboxExecutionEntity.class);
        when(leases.owned(any())).thenReturn(entity);
        when(entity.cancelRequested()).thenReturn(false);
        BrokerProperties properties = new BrokerProperties();
        properties.setWorkspaceRoot(workspace.toString());
        ProviderEnvironment environment = new ProviderEnvironment(
                properties, Map.of("SystemRoot", System.getenv("SystemRoot")), "Windows");
        SandboxWorker worker = new SandboxWorker(
                leases,
                properties,
                new ObjectMapper(),
                new SandboxProcessRegistry(),
                environment,
                new SandboxProviderCommandFactory(properties));
        SandboxLeaseService.Lease lease = new SandboxLeaseService.Lease(
                "execution-1", "owner", "token", 1L, "ACCEPTED", false);
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");

        ReflectionTestUtils.invokeMethod(
                worker, "execute", lease, List.of(java.toString(), "-version"), 10_000L, 65_536L, false);

        verify(leases, atLeastOnce()).heartbeat(lease, Duration.ofSeconds(30));
    }
}
