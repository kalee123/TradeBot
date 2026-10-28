package com.app.tradebot.authentication;

import com.app.tradebot.appsetup.FieldConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);
        boolean auth = isAuthenticated(session);

        if ("/login".equals(path) && auth) {
            response.sendRedirect("/dashboard");
            return;
        }

        if (path.equals("/login") || path.equals("/kite/callback")) {
            chain.doFilter(request, response);
            return;
        }

        if (!auth) {
            response.setStatus(401);
            response.getWriter().write("Not logged in");
            return;
        }

        // Tell Spring Security who the caller is, otherwise .authenticated() rejects the request
        Long userId = (Long) session.getAttribute(FieldConstants.FIELD_USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        chain.doFilter(request, response);
    }

    private boolean isAuthenticated(HttpSession session) {
        if (session == null) {
            return false;
        }
        Boolean auth = (Boolean) session.getAttribute(FieldConstants.FIELD_USER_AUTH);
        if (auth == null || !auth || session.getAttribute(FieldConstants.FIELD_USER_ID) == null) {
            return false;
        }
        // Broker token expired (Kite resets daily at 6 AM IST) -> force a fresh login
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(FieldConstants.FIELD_TOKEN_EXPIRES_AT);
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            session.invalidate();
            return false;
        }
        return true;
    }
}
