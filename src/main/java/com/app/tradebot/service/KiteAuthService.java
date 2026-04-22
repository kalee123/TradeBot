package com.app.tradebot.service;

import com.app.tradebot.appsetup.UrlConstants;
import com.app.tradebot.config.KiteProperties;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;


@Service
public class KiteAuthService {

    private final KiteProperties props;

    public KiteAuthService(KiteProperties props) {
        this.props = props;
    }

    public String getLoginUrl(String state) {
        return String.format(UrlConstants.KITE_LOGIN_URL, props.getApiKey(), state);
    }

    public String exchangeRequestTokenForAccessToken(String requestToken) throws Exception {
        // Step 1: Create checksum and exchange token with Kite API
        String data = props.getApiKey() + requestToken + props.getApiSecret();
        String checksum = sha256Hex(data);

        java.net.URL url = new java.net.URL(UrlConstants.KITE_TOKEN_EXCHANGE_URL);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("X-Kite-Version", "3");
        conn.setDoOutput(true);

        String body = "api_key=" + encode(props.getApiKey()) + "&request_token=" + encode(requestToken) + "&checksum=" + encode(checksum);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        if (status >= 200 && status < 300) {
            String accessToken = parseAccessToken(response);
            if (accessToken == null) {
                throw new RuntimeException("access_token not found in response: " + response);
            }
            return accessToken;
        } else {
            throw new RuntimeException("Token exchange failed: " + status + " " + response);
        }
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String parseAccessToken(String json) {
        JSONObject root = new JSONObject(json);
        // Prefer nested data.access_token if present
        if (root.has("data")) {
            JSONObject data = root.optJSONObject("data");
            if (data != null && data.has("access_token")) {
                return data.optString("access_token", null);
            }
        }
        // Fallback to top-level
        return root.optString("access_token", null);
    }
}

