package com.app.tradebot.models;

public class KiteSession {
    private String accessToken;
    private String publicToken;
    private String refreshToken;
    private String userBrokerId;   // Kite "user_id" (e.g. QEX463)
    private String userName;
    private String userShortname;
    private String email;
    private String broker;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getUserBrokerId() { return userBrokerId; }
    public void setUserBrokerId(String userBrokerId) { this.userBrokerId = userBrokerId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserShortname() { return userShortname; }
    public void setUserShortname(String userShortname) { this.userShortname = userShortname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }
}

