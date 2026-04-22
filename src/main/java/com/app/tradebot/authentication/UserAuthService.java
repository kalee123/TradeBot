package com.app.tradebot.authentication;

import com.app.tradebot.appsetup.FieldConstants;
import com.app.tradebot.entity.UserEntity;
import com.app.tradebot.repository.UserRepository;
import com.app.tradebot.service.KiteAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Service
public class UserAuthService {

    @Autowired
    KiteAuthService authService;

    @Autowired
    @Lazy
    private UserRepository userRepository;

    public String getLoginUrl(String state) {
        return authService.getLoginUrl(state);
    }

    public String authenticateUser(String requestToken) {
        try{
            String accessToken = authService.exchangeRequestTokenForAccessToken(requestToken);

            return accessToken;
        }
        catch (Exception e) {
            throw new RuntimeException("access_token not found in response: " + e);
        }
    }

    public String getUserInfo(String accessToken) {

        return "User info for access token: " + accessToken;
    }
}
