package com.app.tradebot.controller;

import com.app.tradebot.appsetup.FieldConstants;
import com.app.tradebot.entity.UserEntity;
import com.app.tradebot.repository.UserRepository;
import com.app.tradebot.service.KiteAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Scope("request")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    KiteAuthService authService;

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        UserEntity user = new UserEntity();
        user.setUserName("kalidas");
        user.setEmail("kalidas@gmail.com");
        user.setUserBrokerId("qisdo10");
        UserEntity savedUser = userRepository.save(user);
        System.out.println(savedUser.getUserId());
        return "Hello, World!"+savedUser.getUserId();
//        return ResponseEntity.ok("Hello, World!"+savedUser.getUserId());
    }

    @GetMapping("/login")
    @ResponseBody
    public ResponseEntity<Void> login(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(FieldConstants.FIELD_KIETE_STATE, state);
        String url = authService.getLoginUrl(state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/kite/callback")
    public ResponseEntity<String> callback(String request_token, String state, HttpSession session) {
        String saved = (String) session.getAttribute(FieldConstants.FIELD_KIETE_STATE);
        if (state != null && saved != null && !saved.equals(state)) {
            return ResponseEntity.badRequest().body("Invalid state");
        }
        try {
            String accessToken = authService.exchangeRequestTokenForAccessToken(request_token);
            return ResponseEntity.ok(accessToken);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Token exchange failed: " + e.getMessage());
        }
    }
}
