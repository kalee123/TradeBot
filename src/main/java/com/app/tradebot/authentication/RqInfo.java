package com.app.tradebot.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.UUID;

// One instance per HTTP request. The proxy lets singletons (filters, services) inject it
// and always see the instance of the request currently being handled.
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RqInfo {

    private final HttpServletRequest request;
    private final UserAuthService userAuthService;
    private final String requestId = UUID.randomUUID().toString();

    private Long userId;
    private String accessToken;

    public RqInfo(HttpServletRequest request, UserAuthService userAuthService) {
        this.request = request;
        this.userAuthService = userAuthService;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public String getAccessToken() {
        if (accessToken == null) {
            if (userId == null) {
                throw new IllegalStateException("No authenticated user for this request");
            }
            accessToken = userAuthService.getActiveAccessToken(userId)
                    .orElseThrow(() -> new IllegalStateException("No valid broker token, login again"));
        }
        return accessToken;
    }
}
