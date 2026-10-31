package com.app.tradebot.service;

import com.app.tradebot.appsetup.UrlConstants;
import com.app.tradebot.authentication.RqInfo;
import com.app.tradebot.broker.kite.KiteApiClient;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final RqInfo rqInfo;
    private final KiteApiClient kiteApiClient;

    public UserProfileService(RqInfo rqInfo, KiteApiClient kiteApiClient) {
        this.rqInfo = rqInfo;
        this.kiteApiClient = kiteApiClient;
    }

    public String getProfileDetails() throws Exception {
        return kiteApiClient.get(UrlConstants.KITE_USER_PROFILE_PATH, rqInfo.getAccessToken());
    }

    public String getMarginDetails() throws Exception{
        return kiteApiClient.get(UrlConstants.KITE_USER_MARGINS_PATH, rqInfo.getAccessToken());
    }
}
