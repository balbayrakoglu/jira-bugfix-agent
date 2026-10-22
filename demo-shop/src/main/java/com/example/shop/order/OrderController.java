package com.example.shop.order;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/total")
    public Map<String, BigDecimal> total(@RequestBody Order order) {
        return Map.of("total", orderService.calculateTotal(order));
    }
}
