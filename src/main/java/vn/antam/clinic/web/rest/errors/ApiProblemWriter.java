package vn.antam.clinic.web.rest.errors;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiProblemWriter {

    public void write(HttpServletResponse response, HttpStatus status, String title, String detail)
        throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        String requestId = MDC.get("requestId");
        StringBuilder body = new StringBuilder(256);
        body.append("{\"type\":\"about:blank\",\"title\":\"")
            .append(escape(title))
            .append("\",\"status\":")
            .append(status.value())
            .append(",\"detail\":\"")
            .append(escape(detail))
            .append('"');
        if (requestId != null) {
            body.append(",\"requestId\":\"").append(escape(requestId)).append('"');
        }
        body.append('}');
        response.getWriter().write(body.toString());
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
