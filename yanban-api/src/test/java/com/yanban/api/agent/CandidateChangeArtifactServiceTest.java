package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.artifact.AgentArtifactService;
import com.yanban.api.artifact.ArtifactResponse;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectManifestResponse;
import com.yanban.api.project.ProjectService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CandidateChangeArtifactServiceTest {
    private static final String V1 = "b".repeat(64);
    private static final String V2 = "c".repeat(64);
    private static final String H1 = "a".repeat(64);
    private static final String H2 = "d".repeat(64);
    @Test
    void persistedCandidateIsMarkedStaleWhenCurrentProjectHashChanges() throws Exception {
        AgentArtifactService artifacts = mock(AgentArtifactService.class);
        ProjectService projects = mock(ProjectService.class);
        ObjectMapper json = new ObjectMapper();
        CandidateChangeArtifactService service = new CandidateChangeArtifactService(artifacts, projects, json);
        CandidateChangeSet candidate = new CandidateChangeSet(42L, V1, "src/Main.java", H1, "fix", "patch", List.of("e1"), null, null);
        when(artifacts.createArtifact(anyLong(), any())).thenReturn(artifact(9L, json.writeValueAsString(candidate)));
        CandidateChangeSet saved = service.store(7L, 1L, candidate);
        when(artifacts.getArtifact(7L, 9L)).thenReturn(artifact(9L, json.writeValueAsString(saved)));
        when(projects.manifest(7L, 42L)).thenReturn(new ProjectManifestResponse(42L, V2, List.of(
                new ProjectFileEntry("src/Main.java", 1, Instant.EPOCH, H2))));

        CandidateChangeSet reloaded = service.getCurrent(7L, 9L);

        assertThat(reloaded.status()).isEqualTo(CandidateChangeStatus.STALE);
        assertThat(reloaded.applicationStatus()).isEqualTo(CandidateChangeSet.NOT_APPLIED);
    }

    @Test
    void candidateIsStaleWhenProjectVersionChangesEvenIfFileHashDoesNot() throws Exception {
        AgentArtifactService artifacts = mock(AgentArtifactService.class); ProjectService projects = mock(ProjectService.class);
        ObjectMapper json = new ObjectMapper(); CandidateChangeArtifactService service = new CandidateChangeArtifactService(artifacts, projects, json);
        CandidateChangeSet candidate = new CandidateChangeSet(42L, V1, "src/Main.java", H1, "fix", "patch", List.of(), null, null);
        when(artifacts.getArtifact(7L, 9L)).thenReturn(artifact(9L, json.writeValueAsString(candidate)));
        when(projects.manifest(7L, 42L)).thenReturn(new ProjectManifestResponse(42L, V2,
                List.of(new ProjectFileEntry("src/Main.java", 1, Instant.EPOCH, H1))));

        assertThat(service.getCurrent(7L, 9L).status()).isEqualTo(CandidateChangeStatus.STALE);
    }

    @Test
    void legacyCandidateWithoutProjectVersionIsStale() throws Exception {
        AgentArtifactService artifacts = mock(AgentArtifactService.class); ProjectService projects = mock(ProjectService.class);
        ObjectMapper json = new ObjectMapper(); CandidateChangeArtifactService service = new CandidateChangeArtifactService(artifacts, projects, json);
        CandidateChangeSet legacy = new CandidateChangeSet(42L, "src/Main.java", H1, "fix", "patch", List.of(), null, null);
        when(artifacts.getArtifact(7L, 9L)).thenReturn(artifact(9L, json.writeValueAsString(legacy)));
        when(projects.manifest(7L, 42L)).thenReturn(new ProjectManifestResponse(42L, V1,
                List.of(new ProjectFileEntry("src/Main.java", 1, Instant.EPOCH, H1))));

        CandidateChangeSet result = service.getCurrent(7L, 9L);
        assertThat(result.status()).isEqualTo(CandidateChangeStatus.STALE);
        assertThat(result.applicationStatus()).isEqualTo(CandidateChangeSet.NOT_APPLIED);
    }

    @Test
    void otherUserCannotReadCandidateArtifact() {
        AgentArtifactService artifacts = mock(AgentArtifactService.class);
        CandidateChangeArtifactService service = new CandidateChangeArtifactService(artifacts, mock(ProjectService.class), new ObjectMapper());
        when(artifacts.getArtifact(8L, 9L)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getCurrent(8L, 9L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void candidateIsInvalidatedWhenItsProjectBindingHasBeenDeleted() throws Exception {
        AgentArtifactService artifacts = mock(AgentArtifactService.class);
        ProjectService projects = mock(ProjectService.class);
        ObjectMapper json = new ObjectMapper();
        CandidateChangeArtifactService service = new CandidateChangeArtifactService(artifacts, projects, json);
        CandidateChangeSet candidate = new CandidateChangeSet(42L, V1, "src/Main.java", H1, "fix", "patch",
                List.of("e1"), CandidateChangeStatus.CANDIDATE, CandidateChangeSet.NOT_APPLIED, 9L);
        when(artifacts.getArtifact(7L, 9L)).thenReturn(artifact(9L, json.writeValueAsString(candidate)));
        when(projects.manifest(7L, 42L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        CandidateChangeSet reloaded = service.getCurrent(7L, 9L);

        assertThat(reloaded.status()).isEqualTo(CandidateChangeStatus.INVALIDATED);
        assertThat(reloaded.applicationStatus()).isEqualTo(CandidateChangeSet.NOT_APPLIED);
        assertThat(reloaded.artifactId()).isEqualTo(9L);
    }

    private ArtifactResponse artifact(Long id, String content) {
        return new ArtifactResponse(id, 7L, 1L, "candidate.md", "MARKDOWN", content,
                CandidateChangeArtifactService.SOURCE_TYPE, List.of(), "ACTIVE", null, null, null, null, null);
    }
}
