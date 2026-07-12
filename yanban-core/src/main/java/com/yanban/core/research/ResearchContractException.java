package com.yanban.core.research;

public final class ResearchContractException extends IllegalArgumentException {
    private final ResearchToolErrorCode errorCode;

    public ResearchContractException(ResearchToolErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ResearchToolErrorCode errorCode() {
        return errorCode;
    }
}
