package com.app.algodesk.controller;

import com.app.algodesk.service.OrderService;
import com.app.algodesk.service.PortfolioService;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final OrderService orderService;

    public PortfolioController(PortfolioService portfolioService, OrderService orderService) {
        this.portfolioService = portfolioService;
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public ResponseEntity<String> orders() {
        return respond(orderService::getOrders, "Could not load orders");
    }

    @GetMapping("/holdings")
    public ResponseEntity<String> holdings() {
        return respond(portfolioService::getHoldings, "Could not load holdings");
    }

    @GetMapping("/positions")
    public ResponseEntity<String> positions() {
        return respond(portfolioService::getPositions, "Could not load positions");
    }

    private static ResponseEntity<String> respond(Callable<String> call, String errorMessage) {
        try {
            return ResponseEntity.ok(call.call());
        } catch (Exception e) {
            return ResponseEntity.status(502).body(new JSONObject().put("error", errorMessage).toString());
        }
    }
}
