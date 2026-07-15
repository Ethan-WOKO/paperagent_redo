package com.yanban.api.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRevisionRepository extends JpaRepository<ProjectRevision, Long> {
    List<ProjectRevision> findByProjectIdAndUserIdOrderByCreatedAtDescIdDesc(Long projectId, Long userId);
    Optional<ProjectRevision> findByIdAndProjectIdAndUserId(Long id, Long projectId, Long userId);
}
