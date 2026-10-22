package com.example.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private final OrderService service = new OrderService();

    @Test
    void appliesCouponAndFreeShippingAboveThreshold() {
        Order order = new Order("o-1", "c-1",
                List.of(new OrderLine("BOOK", 2, new BigDecimal("40.00"))),
                new Coupon("AUTUMN10", 10));

        assertThat(service.calculateTotal(order)).isEqualByComparingTo("72.00");
    }

    @Test
    void chargesShippingBelowThreshold() {
        Order order = new Order("o-2", "c-1",
                List.of(new OrderLine("PEN", 3, new BigDecimal("5.00"))),
                new Coupon("NONE", 0));

        assertThat(service.calculateTotal(order)).isEqualByComparingTo("19.99");
    }

    @Test
    void capsDiscountAtFiftyPercent() {
        Order order = new Order("o-3", "c-2",
                List.of(new OrderLine("LAMP", 1, new BigDecimal("200.00"))),
                new Coupon("MEGA90", 90));

        assertThat(service.calculateTotal(order)).isEqualByComparingTo("100.00");
    }
}
