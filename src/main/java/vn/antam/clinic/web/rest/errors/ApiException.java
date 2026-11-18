package vn.antam.clinic.web.rest.errors;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final java.util.Map<String, Object> extensions;

    public ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, java.util.Map.of());
    }

    public ApiException(
        HttpStatus status,
        String title,
        String detail,
        java.util.Map<String, Object> extensions
    ) {
        super(detail);
        this.status = status;
        this.title = title;
        this.extensions = java.util.Map.copyOf(extensions);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public java.util.Map<String, Object> getExtensions() {
        return extensions;
    }

    public ApiException withCode(String code) {
        var values = new java.util.HashMap<>(extensions);
        values.put("code", code);
        return new ApiException(status, title, getMessage(), values);
    }
}
