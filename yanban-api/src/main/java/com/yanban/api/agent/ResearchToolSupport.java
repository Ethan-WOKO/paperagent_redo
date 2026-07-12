package com.yanban.api.agent;

import com.yanban.api.project.ProjectFileEntry;
import com.yanban.core.research.FileHash;
import com.yanban.core.research.ParserVersionRef;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchBudgetUsage;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchToolErrorCode;
import com.yanban.core.research.ResearchToolItem;
import com.yanban.core.research.ResearchToolOutcome;
import com.yanban.core.research.ResearchToolResultState;
import com.yanban.core.research.SourceRange;
import com.yanban.core.research.TrustLabel;
import java.util.LinkedHashSet;
import java.util.List;

final class ResearchToolSupport {
    private ResearchToolSupport() { }

    static ResearchEvidenceRef evidence(AbstractResearchProjectToolExecutor.ResearchContext context,
                                        ProjectRelativePath path, ProjectFileEntry file, int startLine, int endLine,
                                        String parserVersion) {
        return new ResearchEvidenceRef(context.projectVersion(), path, new FileHash(file.sha256()),
                new SourceRange(startLine, endLine), new ParserVersionRef(parserVersion),
                TrustLabel.SERVER_ATTESTED_METADATA);
    }

    static ResearchToolOutcome outcome(List<? extends ResearchToolItem> items, List<ResearchEvidenceRef> evidence,
                                       boolean partial, boolean truncated, ResearchBudgetUsage usage) {
        List<ResearchEvidenceRef> envelope = List.copyOf(new LinkedHashSet<>(evidence));
        if (items.isEmpty()) {
            if (partial || truncated) {
                // ResearchToolOutcome deliberately forbids item-less PARTIAL/TRUNCATED.  A
                // no-item abnormal execution must therefore remain observable as failure.
                return parseFailed(usage);
            }
            return new ResearchToolOutcome(ResearchToolResultState.EMPTY, List.of(), List.of(), null, usage);
        }
        if (truncated) {
            return new ResearchToolOutcome(ResearchToolResultState.TRUNCATED, List.copyOf(items), envelope,
                    ResearchToolErrorCode.RESULT_TRUNCATED, usage);
        }
        if (partial) {
            return new ResearchToolOutcome(ResearchToolResultState.PARTIAL, List.copyOf(items), envelope,
                    ResearchToolErrorCode.PARTIAL_RESULT, usage);
        }
        return new ResearchToolOutcome(ResearchToolResultState.COMPLETE, List.copyOf(items), envelope, null, usage);
    }

    static ResearchToolOutcome parseFailed(ResearchBudgetUsage usage) {
        return new ResearchToolOutcome(ResearchToolResultState.PARSE_FAILED, List.of(), List.of(),
                ResearchToolErrorCode.PARSER_FAILURE, usage);
    }

    static String line(String[] lines, int oneBased) {
        return lines[Math.max(0, Math.min(lines.length - 1, oneBased - 1))];
    }
}
