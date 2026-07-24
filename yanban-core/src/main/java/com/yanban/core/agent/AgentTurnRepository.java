package com.yanban.core.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTurnRepository extends JpaRepository<AgentTurn, Long> {
    List<AgentTurn> findBySessionIdAndUserIdOrderByStartedAtDescIdDesc(Long sessionId, Long userId);

    void deleteBySessionId(Long sessionId);
}
