package vn.antam.clinic.web.rest.errors;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.antam.clinic.integration.ai.AiHttpClient;

@RestControllerAdvice
public class ChatExceptionHandler {

    @ExceptionHandler(AiHttpClient.RemoteFailure.class)
    public ResponseEntity<ProblemDetail> remote(AiHttpClient.RemoteFailure error) {
        HttpStatus status = error.retryable
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.BAD_REQUEST;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        p.setTitle("Kết nối AI/vector chưa sẵn sàng");
        return ResponseEntity.status(status).body(p);
    }
}
