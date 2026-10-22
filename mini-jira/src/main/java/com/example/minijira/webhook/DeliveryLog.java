package com.example.minijira.webhook;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeliveryLog {

    private static final int CAPACITY = 100;

    public record Delivery(Instant at, String issueKey, String event, int attempt, Integer httpStatus,
                           String responseBody, String error) {

        public boolean success() {
            return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
        }
    }

    private final Deque<Delivery> deliveries = new ArrayDeque<>();

    public synchronized void record(Delivery delivery) {
        deliveries.addFirst(delivery);
        while (deliveries.size() > CAPACITY) {
            deliveries.removeLast();
        }
    }

    public synchronized List<Delivery> recent() {
        return List.copyOf(deliveries);
    }
}
