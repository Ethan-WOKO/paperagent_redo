package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SandboxWorkerCreateTimeoutTest {
    @Test
    void allowsMeasuredColdCreateWhileRemainingFinite() {
        assertThat(SandboxWorker.createTimeoutMillis())
                .isEqualTo(60_000L)
                .isGreaterThan(29_400L)
                .isLessThanOrEqualTo(60_000L);
    }
}
