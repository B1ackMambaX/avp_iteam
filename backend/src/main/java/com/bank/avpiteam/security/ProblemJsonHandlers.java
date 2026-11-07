package com.bank.avpiteam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401 и 403 в формате RFC 7807. Текст не раскрывает, существует ли пользователь. */
@Component
class ProblemJsonHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    ProblemJsonHandlers(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e)
            throws IOException {
        res.setHeader("WWW-Authenticate", "Basic realm=\"avp_iteam\"");
        write(res, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException e)
            throws IOException {
        write(res, HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permissions");
    }

    private void write(HttpServletResponse res, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), pd);
    }
}