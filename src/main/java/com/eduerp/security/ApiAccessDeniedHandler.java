package com.eduerp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Keeps authorization failures distinguishable from expired-session failures without disclosing credentials. */
@Component
@Slf4j
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authorities = authentication == null ? "none" : authentication.getAuthorities().toString();
        log.warn("Forbidden API request rejected: method={} path={} authenticated={} authorities={} reason={}",
                request.getMethod(), request.getRequestURI(), authentication != null && authentication.isAuthenticated(),
                authorities, accessDeniedException.getClass().getSimpleName());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":403,\"message\":\"You are not authorized to perform this operation.\"}");
    }
}
