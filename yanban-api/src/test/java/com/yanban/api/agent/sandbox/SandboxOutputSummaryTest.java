package com.yanban.api.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SandboxOutputSummaryTest {
    @Test void outputIsSmallTailedAndRedactsSecretsAndHostPaths() {
        StringBuilder input = new StringBuilder();
        for (int i=0;i<100;i++) input.append("line-").append(i).append('-').append("x".repeat(100)).append('\n');
        input.append("Authorization: Bearer bearer-secret\n")
                .append("password=\"hunter2 quoted\"\n")
                .append("https://example.invalid/result?token=query-secret&ok=true\n")
                .append("C:\\Users\\alice\\secret.txt\n")
                .append("/var/lib/yanban/work/source.java\n");
        String summary = SandboxOutputSummary.summarize(input.toString());
        assertThat(summary).hasSizeLessThanOrEqualTo(4096).contains("<redacted>","<host-path>")
                .doesNotContain("bearer-secret","hunter2","query-secret","C:\\Users","/var/lib");
    }
}
