package vn.antam.clinic.web.rest.errors;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkflowConflictAdvice {

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> conflict() {
        var body = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Thao tác khác đang cập nhật dữ liệu. Tải lại và thử lại với cùng mã chống lặp."
        );
        body.setTitle("Dữ liệu đang thay đổi");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
