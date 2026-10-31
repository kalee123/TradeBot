package com.app.tradebot.utils;

import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

import static com.app.tradebot.appsetup.UrlConstants.BASE_URL;

public class ApiClientUtil {

    private static final String KITE_VERSION = "3";

    public String get(String path, String apiKey, String accessToken) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Kite-Version", KITE_VERSION);
        conn.setRequestProperty(
                "Authorization",
                "token " + apiKey + ":" + accessToken
        );

        String response = connectionParser(conn);
        return response;
    }

    public String connectionParser(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (status >= 200 && status < 300) {
            return response;
        } else {
            throw new RuntimeException("API call failed: " + status + " " + response);
        }
    }

}