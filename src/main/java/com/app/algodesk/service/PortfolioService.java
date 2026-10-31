package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import org.springframework.stereotype.Service;

@Service
public class PortfolioService {

    private final RqInfo rqInfo;
    private final KiteApiClient kiteApiClient;

    public PortfolioService(RqInfo rqInfo, KiteApiClient kiteApiClient) {
        this.rqInfo = rqInfo;
        this.kiteApiClient = kiteApiClient;
    }

    public String getHoldings() throws Exception {
        return kiteApiClient.getData(UrlConstants.KITE_HOLDINGS_PATH, rqInfo.getAccessToken());
    }

    public String getPositions() throws Exception {
        return kiteApiClient.getData(UrlConstants.KITE_POSITIONS_PATH, rqInfo.getAccessToken());
    }
}
