package com.eduerp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiSecurityHandlerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredOrMissingAuthenticationProducesSafe401() throws Exception {
        HttpServletRequest request = request("POST", "/api/fees/me/payments/initiate");
        ResponseCapture capture = response();

        new ApiAuthenticationEntryPoint().commence(request, capture.response,
                new InsufficientAuthenticationException("authentication required"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, capture.status);
        assertTrue(capture.body.toString().contains("session has expired"));
    }

    @Test
    void authenticatedStudentForbiddenByAuthorizationProducesSafe403() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "student", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        HttpServletRequest request = request("POST", "/api/fees/me/payments/confirm");
        ResponseCapture capture = response();

        new ApiAccessDeniedHandler().handle(request, capture.response, new AccessDeniedException("forbidden"));

        assertEquals(HttpServletResponse.SC_FORBIDDEN, capture.status);
        assertTrue(capture.body.toString().contains("not authorized"));
    }

    private HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    private ResponseCapture response() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new ResponseCapture(response, body);
    }

    private static final class ResponseCapture {
        private final HttpServletResponse response;
        private final StringWriter body;
        private int status;

        private ResponseCapture(HttpServletResponse response, StringWriter body) {
            this.response = new StatusCapturingResponse(response, this);
            this.body = body;
        }
    }

    private static final class StatusCapturingResponse extends HttpServletResponseWrapper {
        private final ResponseCapture capture;

        private StatusCapturingResponse(HttpServletResponse delegate, ResponseCapture capture) {
            super(delegate);
            this.capture = capture;
        }

        @Override public void setStatus(int sc) {
            capture.status = sc;
            super.setStatus(sc);
        }
    }
}
