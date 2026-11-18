package vn.antam.clinic.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.antam.clinic.service.dto.shared.FieldErrorItem;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> apiException(
        ApiException exception,
        HttpServletRequest request
    ) {
        if (exception.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) log.debug(
            "Capacity or dependency unavailable at {}",
            request.getRequestURI(),
            exception
        );
        var response = problem(
            exception.getStatus(),
            exception.getTitle(),
            exception.getMessage(),
            request,
            null
        );
        exception.getExtensions().forEach(response.getBody()::setProperty);
        if (
            exception.getStatus() == HttpStatus.SERVICE_UNAVAILABLE ||
            exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS
        ) return ResponseEntity.status(exception.getStatus())
            .headers(response.getHeaders())
            .header(
                "Retry-After",
                exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS ? "60" : "2"
            )
            .body(response.getBody());
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<FieldErrorItem> errors = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error ->
                new FieldErrorItem(
                    error.getField(),
                    error.getDefaultMessage() == null
                        ? "Giá trị không hợp lệ"
                        : error.getDefaultMessage()
                )
            )
            .toList();
        return problem(
            HttpStatus.BAD_REQUEST,
            "Dữ liệu không hợp lệ",
            "Hãy kiểm tra các trường được đánh dấu.",
            request,
            errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Dữ liệu không đọc được",
            "Nội dung gửi lên không đúng định dạng JSON.",
            request,
            null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> typeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Tham số không hợp lệ",
            "Giá trị của " + exception.getName() + " không đúng kiểu.",
            request,
            null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.FORBIDDEN,
            "Không có quyền",
            "Bạn không có quyền thực hiện thao tác này.",
            request,
            null
        );
    }

    @ExceptionHandler({ InvalidDataAccessApiUsageException.class })
    public ResponseEntity<ProblemDetail> badQuery(
        InvalidDataAccessApiUsageException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Truy vấn không hợp lệ",
            "Tham số phân trang hoặc sắp xếp không được hỗ trợ.",
            request,
            null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Dữ liệu bị trùng",
            "Mã hoặc thông tin duy nhất đã tồn tại.",
            request,
            null
        );
    }

    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> concurrentUpdate(
        org.springframework.dao.ConcurrencyFailureException exception,
        HttpServletRequest request
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Dữ liệu đang được cập nhật",
            "Có thao tác đồng thời. Hãy tải lại dữ liệu và thử lại.",
            request,
            null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled request {}", MDC.get("requestId"), exception);
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Lỗi hệ thống",
            "Không xử lý được yêu cầu. Vui lòng thử lại.",
            request,
            null
        );
    }

    private ResponseEntity<ProblemDetail> problem(
        HttpStatus status,
        String title,
        String detail,
        HttpServletRequest request,
        List<FieldErrorItem> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
