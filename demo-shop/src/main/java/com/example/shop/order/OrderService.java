package com.example.shop.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    static final BigDecimal SHIPPING_FEE = new BigDecimal("4.99");
    static final int MAX_PERCENT_OFF = 50;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal calculateTotal(Order order) {
        BigDecimal subtotal = order.lines().stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal afterDiscount = subtotal.subtract(discountFor(order.coupon(), subtotal));
        BigDecimal shipping = afterDiscount.compareTo(FREE_SHIPPING_THRESHOLD) > 0 ? BigDecimal.ZERO : SHIPPING_FEE;

        return afterDiscount.add(shipping).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        int percent = Math.min(coupon.percentOff(), MAX_PERCENT_OFF);
        return subtotal.multiply(BigDecimal.valueOf(percent)).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
