package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class E2bCommandFactoryTest {
    @Test
    void passesOnlyStructuredArgumentsToPinnedHelper() {
        E2bCommandFactory factory = new E2bCommandFactory("/opt/e2b/bin/python", "/app/e2b_provider.py",
                "yanban-research-v1");
        assertThat(factory.provider()).isEqualTo("e2b");
        Path workspace = Path.of("/work/1");
        assertThat(factory.create("yb-1", workspace, 2, 536870912L, 900000L))
                .containsExactly("/opt/e2b/bin/python", "/app/e2b_provider.py", "create", "--name", "yb-1",
                        "--workspace", workspace.toString(), "--template", "yanban-research-v1", "--cpus", "2",
                        "--memory-bytes", "536870912", "--timeout-millis", "900000");
        assertThat(factory.exec("yb-1", List.of("yanban-runner", "python", "study.py")))
                .containsExactly("/opt/e2b/bin/python", "/app/e2b_provider.py", "exec", "--name", "yb-1",
                        "--", "yanban-runner", "python", "study.py");
        assertThat(factory.verifyNetworkPolicy("yb-1"))
                .containsExactly("/opt/e2b/bin/python", "/app/e2b_provider.py", "policy", "--name", "yb-1");
    }
}
