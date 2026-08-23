package com.example.exportflow.order.dev;

import com.example.exportflow.order.domain.*;
import com.example.exportflow.order.infrastructure.OrderMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Profile("dev")
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "orders", matchIfMissing = false)
public class OrderDataSeeder implements ApplicationRunner {
    private static final String[] NAMES = {"张伟", "王芳", "李娜", "刘洋", "陈晨", "杨静", "赵磊", "黄敏"};
    private static final String[] PROVINCES = {"北京", "上海", "广东", "浙江", "江苏", "四川", "湖北", "山东"};
    private final OrderMapper mapper;
    private final Environment environment;

    public OrderDataSeeder(OrderMapper mapper, Environment environment) {
        this.mapper = mapper;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        int target = environment.getProperty("app.seed.orders", Integer.class, 0);
        seedTo(target);
    }

    public void seedTo(int target) {
        long existing = mapper.countAll();
        if (existing >= target) return;
        Random random = new Random(20260822L + existing);
        for (long start = existing + 1; start <= target; start += 2_000) {
            int size = (int) Math.min(2_000, target - start + 1);
            List<Order> batch = new ArrayList<>(size);
            for (int offset = 0; offset < size; offset++) {
                long sequence = start + offset;
                LocalDateTime created = LocalDateTime.of(2025, 1, 1, 0, 0).plusMinutes(sequence % 800_000);
                OrderStatus orderStatus = OrderStatus.values()[random.nextInt(OrderStatus.values().length)];
                PaymentStatus paymentStatus = orderStatus == OrderStatus.PENDING_PAYMENT
                        ? PaymentStatus.UNPAID : PaymentStatus.values()[1 + random.nextInt(2)];
                LocalDateTime paidAt = paymentStatus == PaymentStatus.UNPAID ? null : created.plusMinutes(1 + random.nextInt(300));
                batch.add(new Order(
                        0,
                        "ORD" + String.format("%012d", sequence),
                        NAMES[random.nextInt(NAMES.length)],
                        "1" + String.format("%010d", sequence % 10_000_000_000L),
                        orderStatus,
                        paymentStatus,
                        PaymentMethod.values()[random.nextInt(PaymentMethod.values().length)],
                        OrderSource.values()[random.nextInt(OrderSource.values().length)],
                        1 + random.nextInt(8),
                        BigDecimal.valueOf(100 + random.nextInt(100_000), 2),
                        PROVINCES[random.nextInt(PROVINCES.length)],
                        created,
                        paidAt,
                        created.plusHours(random.nextInt(72))
                ));
            }
            mapper.insertBatch(batch);
        }
    }
}
