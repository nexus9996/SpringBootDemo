package com.example.demo.common.generator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器：{@code ORD} + 业务日期(yyyyMMdd) + 10 位随机码（去掉易混字符的 32 进制字母表）。
 *
 * <p>不依赖数据库序列，单机不重复概率足够（32^10），跨机也无需协调；业务日期使用可注入的 {@link Clock}，
 * 便于测试与多时区部署。
 */
@Component
public class OrderNumberGenerator {

    /** 去除 I/O/0/1 等易混字符的字母表。 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SUFFIX_LENGTH = 10;
    private static final String PREFIX = "ORD";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderNumberGenerator() {
        this(Clock.systemDefaultZone());
    }

    OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        var builder = new StringBuilder(PREFIX.length() + 8 + SUFFIX_LENGTH)
                .append(PREFIX)
                .append(LocalDate.now(clock).format(DATE_FORMAT));
        for (var i = 0; i < SUFFIX_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
