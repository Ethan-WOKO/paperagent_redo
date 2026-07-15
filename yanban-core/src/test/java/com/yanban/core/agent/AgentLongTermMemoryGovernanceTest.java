package com.yanban.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AgentLongTermMemoryGovernanceTest {

    @Test
    void exposesOnlyTheMinimumTrustedConfirmationAndProvenanceVocabulary() {
        assertThat(AgentLongTermMemory.CONFIRMED_SOURCE_USER_ACTION).isEqualTo("USER_ACTION");
        assertThat(AgentLongTermMemory.PROVENANCE_USER_MESSAGE).isEqualTo("USER_MESSAGE");
    }

    @Test
    void newAndCorrectedRowsRemainUnconfirmedUntilAnExplicitGovernanceFlowRuns() {
        AgentLongTermMemory memory = new AgentLongTermMemory(
                42L,
                null,
                AgentLongTermMemory.SCOPE_USER,
                AgentLongTermMemory.TYPE_FACT,
                "User prefers concise explanations.",
                "[\"style\"]",
                AgentLongTermMemory.SOURCE_USER_CONFIRMED,
                "message:1",
                BigDecimal.valueOf(0.9),
                null);

        assertThat(memory.getConfirmationStatus()).isEqualTo(AgentLongTermMemory.CONFIRMATION_UNCONFIRMED);
        assertThat(memory.getConfirmedAt()).isNull();
        assertThat(memory.getConfirmedSource()).isNull();
        assertThat(memory.getProvenanceType()).isNull();
        assertThat(memory.getProvenanceRef()).isNull();
        assertThat(memory.getProjectVersion()).isNull();
        assertThat(memory.getExpiresAt()).isNull();
        assertThat(memory.getInvalidatedAt()).isNull();
        assertThat(memory.getInvalidationReason()).isNull();
    }
}
