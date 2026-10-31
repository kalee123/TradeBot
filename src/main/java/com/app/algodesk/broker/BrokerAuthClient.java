package com.app.algodesk.broker;

public interface BrokerAuthClient {

    String getLoginUrl(String state);

    BrokerSession createSession(String requestToken) throws Exception;

    String getApiKey();
}
