package com.yanban.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yanban.paper.domain.PaperSectionRepository;
import com.yanban.paper.domain.PaperTask;
import com.yanban.paper.domain.PaperTaskArtifactRepository;
import com.yanban.paper.domain.PaperTaskClarificationRepository;
import com.yanban.paper.domain.PaperTaskRepository;
import com.yanban.paper.domain.PaperTaskRoundRepository;
import com.yanban.paper.latex.LatexParserService;
import com.yanban.paper.latex.LatexRoleRecognitionService;
import com.yanban.paper.literature.LiteratureService;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PaperOrchestratorCancellationTest {

    private static final Long USER_ID = 7L;
    private static final Long TASK_ID = 42L;

    private PaperTaskRepository tasks;
    private PaperEventStreamService eventStreamService;
    private PaperOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        tasks = mock(PaperTaskRepository.class);
        eventStreamService = mock(PaperEventStreamService.class);
        orchestrator = new PaperOrchestrator(
                tasks,
                mock(PaperTaskRoundRepository.class),
                mock(PaperSectionRepository.class),
                mock(PaperTaskArtifactRepository.class),
                mock(PaperTaskClarificationRepository.class),
                eventStreamService,
                mock(PaperStorageService.class),
                mock(LatexParserService.class),
                mock(LatexRoleRecognitionService.class),
                mock(PaperClarificationService.class),
                mock(PaperResearchProfileService.class),
                mock(PaperIntroductionAnalysisService.class),
                mock(LiteratureService.class),
                mock(PaperGapAnalysisService.class),
                mock(PaperSectionPolishService.class),
                mock(PaperAssembleService.class),
                directExecutor()
        );
    }

    @Test
    void stopNonRunningTaskCancelsImmediately() {
        PaperTask task = task("RUNNING", "POLISH");
        when(tasks.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));

        orchestrator.stop(USER_ID, TASK_ID);

        assertThat(task.getStatus()).isEqualTo(PaperOrchestrator.STATUS_CANCELLED);
        assertThat(task.getCurrentStage()).isEqualTo(PaperOrchestrator.STAGE_CANCELLED);
        assertThat(task.getErrorMessage()).isEqualTo("任务已取消");
        assertEventTypes("cancel_requested", "cancelled");
    }

    @Test
    void stopRunningTaskRequestsCancellationUntilCheckpoint() {
        PaperTask task = task("RUNNING", "RETRIEVE");
        markRunning(TASK_ID);
        when(tasks.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));

        orchestrator.stop(USER_ID, TASK_ID);

        assertThat(task.getStatus()).isEqualTo(PaperOrchestrator.STATUS_CANCEL_REQUESTED);
        assertThat(task.getCurrentStage()).isEqualTo("RETRIEVE");
        assertThat(task.getErrorMessage()).isNull();
        assertEventTypes("cancel_requested");
    }

    @Test
    void checkpointMovesRequestedCancellationToCancelling() {
        PaperTask task = task(PaperOrchestrator.STATUS_CANCEL_REQUESTED, "RETRIEVE");
        markRunning(TASK_ID);
        when(tasks.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task));
        orchestrator.stop(USER_ID, TASK_ID);

        assertThatThrownBy(() -> orchestrator.checkpoint(TASK_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("任务已取消");

        assertThat(task.getStatus()).isEqualTo(PaperOrchestrator.STATUS_CANCELLING);
        assertThat(task.getCurrentStage()).isEqualTo("RETRIEVE");
        assertEventTypes("cancel_requested", "cancelling");
    }

    @Test
    void transitionCancelledWritesFinalCancelledEvent() {
        PaperTask task = task(PaperOrchestrator.STATUS_CANCELLING, "RETRIEVE");
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task));

        orchestrator.transitionCancelled(TASK_ID, "任务已取消");

        assertThat(task.getStatus()).isEqualTo(PaperOrchestrator.STATUS_CANCELLED);
        assertThat(task.getCurrentStage()).isEqualTo(PaperOrchestrator.STAGE_CANCELLED);
        assertThat(task.getErrorMessage()).isEqualTo("任务已取消");
        assertEventTypes("cancelled");
    }

    @Test
    void stopTerminalTaskIsIdempotent() {
        PaperTask task = task(PaperOrchestrator.STATUS_COMPLETED, "COMPLETE");
        when(tasks.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));

        orchestrator.stop(USER_ID, TASK_ID);

        assertThat(task.getStatus()).isEqualTo(PaperOrchestrator.STATUS_COMPLETED);
        assertThat(task.getCurrentStage()).isEqualTo("COMPLETE");
        assertNoEvents();
    }

    @Test
    void stopRejectsTaskOwnedByAnotherUser() {
        when(tasks.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.stop(USER_ID, TASK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertNoEvents();
    }

    private PaperTask task(String status, String stage) {
        PaperTask task = new PaperTask(USER_ID, "paper", "main.tex", "paper/main.tex", status, "zh", stage, null);
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        return task;
    }

    @SuppressWarnings("unchecked")
    private void markRunning(Long taskId) {
        Set<Long> runningTasks = (Set<Long>) ReflectionTestUtils.getField(orchestrator, "runningTasks");
        assertThat(runningTasks).isNotNull();
        runningTasks.add(taskId);
    }

    private void assertEventTypes(String... types) {
        ArgumentCaptor<PaperSseEvent> captor = ArgumentCaptor.forClass(PaperSseEvent.class);
        verify(eventStreamService, org.mockito.Mockito.times(types.length)).publish(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PaperSseEvent::type)
                .containsExactly(types);
    }

    private void assertNoEvents() {
        verify(eventStreamService, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
