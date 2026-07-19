package com.yanban.sandboxbroker;

import com.yanban.sandbox.contract.SandboxErrorCode;
import com.yanban.sandbox.contract.SandboxErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="yanban.broker",name="enabled",havingValue="true")
final class BrokerErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<SandboxErrorResponse> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        SandboxErrorCode code = switch (status.value()) {
            case 401, 403 -> SandboxErrorCode.UNAUTHORIZED;
            case 409 -> exception.getReason() != null && exception.getReason().contains("fence")
                    ? SandboxErrorCode.STALE_FENCE : SandboxErrorCode.DIGEST_CONFLICT;
            case 413 -> SandboxErrorCode.REQUEST_TOO_LARGE;
            case 429 -> SandboxErrorCode.CONCURRENCY_EXHAUSTED;
            default -> status.is5xxServerError() ? SandboxErrorCode.PROVIDER_UNAVAILABLE : SandboxErrorCode.PROVIDER_REJECTED;
        };
        return ResponseEntity.status(status).body(new SandboxErrorResponse(code, "sandbox request rejected"));
    }
}
