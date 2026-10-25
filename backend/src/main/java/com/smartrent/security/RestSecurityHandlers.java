package com.smartrent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.dto.Dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Filter-level 401/403 for /api/** requests, in the same JSON shape as GlobalExceptionHandler.
 * (Errors raised inside controllers/services are handled by the @RestControllerAdvice instead.)
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RestSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        write(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required. Please log in.");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        if (!req.getRequestURI().startsWith("/api/")) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (ex instanceof CsrfException) {
            write(req, res, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID",
                    "Missing or invalid CSRF token. Send the XSRF-TOKEN cookie value in the X-XSRF-TOKEN header.");
        } else {
            write(req, res, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.");
        }
    }

    private void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code, String msg)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(),
                new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, msg,
                        req.getRequestURI(), null));
    }
}
