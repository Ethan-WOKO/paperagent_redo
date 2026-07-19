package com.yanban.api.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SandboxPendingProjectionTest {
    @Test void verifiedReceiptRemainsRecoverableUntilFencedProjectionFinishes() {
        SandboxOutboxExecution value = new SandboxOutboxExecution("execution", 1, 2, 3, 4, 5, 7,
                "key", "a".repeat(64), "b".repeat(64), "c".repeat(64), "sensitive-source");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        value.stageReceipt("d".repeat(64), "verified-receipt", now);
        assertThat(value.status()).isEqualTo("RECEIPT_PENDING_PROJECTION");
        assertThat(value.requestJson()).isEqualTo("sensitive-source");
        assertThat(value.receiptJson()).isEqualTo("verified-receipt");
        value.deferProjection(now.plusSeconds(1));
        assertThat(value.status()).isEqualTo("RECEIPT_PENDING_PROJECTION");
        assertThat(value.requestJson()).isNotNull();
        value.finishProjection("SUCCEEDED", null, now.plusSeconds(2));
        assertThat(value.status()).isEqualTo("SUCCEEDED");
        assertThat(value.requestJson()).isNull();
        assertThat(value.receiptJson()).isEqualTo("verified-receipt");
    }
}
