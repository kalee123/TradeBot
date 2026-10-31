package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final RqInfo rqInfo;
    private final KiteApiClient kiteApiClient;

    public OrderService(RqInfo rqInfo, KiteApiClient kiteApiClient) {
        this.rqInfo = rqInfo;
        this.kiteApiClient = kiteApiClient;
    }

    // Today's orders (Kite only returns orders placed during the current trading day)
    public String getOrders() throws Exception {
        return kiteApiClient.getData(UrlConstants.KITE_ORDERS_PATH, rqInfo.getAccessToken());
    }
}
