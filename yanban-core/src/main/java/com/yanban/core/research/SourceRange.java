package com.yanban.core.research;

/** Inclusive, one-based source line range. */
public record SourceRange(int startLine, int endLine) {
    public SourceRange {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("source range must be one-based and ordered");
        }
    }
}
