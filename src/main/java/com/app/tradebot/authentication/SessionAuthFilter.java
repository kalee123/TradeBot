package com.app.tradebot.authentication;

import com.app.tradebot.appsetup.FieldConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Boolean auth = (Boolean) request.getSession().getAttribute(FieldConstants.FIELD_USER_AUTH);

        if ("/login".equals(path) && auth != null && auth) {
            response.sendRedirect("/dashboard");
            return;
        }

        if (path.equals("/login") || path.equals("/kite/callback")) {
            chain.doFilter(request, response);
            return;
        }

        if (auth == null || !auth) {
            response.setStatus(401);
            response.getWriter().write("Not logged in");
            return;
        }

        chain.doFilter(request, response);
    }
}