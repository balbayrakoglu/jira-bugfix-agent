package com.example.shop.order;

import java.util.List;

public record Order(String id, String customerId, List<OrderLine> lines, Coupon coupon) {
}
