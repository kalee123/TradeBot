package com.app.tradebot.controller;

import com.app.tradebot.appsetup.FieldConstants;
import com.app.tradebot.authentication.UserAuthService;
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
    UserAuthService userAuthService;

    @GetMapping("/login")
    @ResponseBody
    public ResponseEntity<Void> login(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(FieldConstants.FIELD_KIETE_STATE, state);
        String url = userAuthService.getLoginUrl(state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/kite/callback")
    public ResponseEntity<String> callback(String request_token, String state, HttpSession session) {
        String saved = (String) session.getAttribute(FieldConstants.FIELD_KIETE_STATE);
        if (state != null && saved != null && !saved.equals(state)) {
            return ResponseEntity.badRequest().body("Invalid state");
        }
        try {
            String response = userAuthService.authenticateUser(request_token);
            if(response.equals("success")) {
                return ResponseEntity.ok("Authentication successful");
            } else {
                return ResponseEntity.status(500).body("Authentication failed: " + response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Token exchange failed: " + e.getMessage());
        }
    }
}
