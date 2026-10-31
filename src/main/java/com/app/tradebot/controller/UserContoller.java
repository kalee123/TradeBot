package com.app.tradebot.controller;

import com.app.tradebot.service.UserProfileService;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserContoller {

    private static final Resource DASHBOARD_PAGE = new ClassPathResource("static/dashboard.html");

    private final UserProfileService userProfileService;

    public UserContoller(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // The page itself; it loads its data from /api/dashboard
    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    public Resource dashBoardPage() {
        return DASHBOARD_PAGE;
    }

    @GetMapping(value = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> dashBoard() {
        try {
            // Kite wraps every response as {"status": "...", "data": {...}}; keep only the data part
            JSONObject profile = new JSONObject(userProfileService.getProfileDetails()).getJSONObject("data");
            JSONObject margin = new JSONObject(userProfileService.getMarginDetails()).getJSONObject("data");

            JSONObject result = new JSONObject();
            result.put("profile_details", profile);
            result.put("margin_details", margin);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(new JSONObject().put("error", "Could not load dashboard").toString());
        }
    }
}
