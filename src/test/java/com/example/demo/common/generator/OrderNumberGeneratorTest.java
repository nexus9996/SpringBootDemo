package com.example.demo.common.generator;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {

    private static final Pattern FORMAT = Pattern.compile("^ORD\\d{8}[A-HJ-NP-Z2-9]{10}$");

    private final OrderNumberGenerator generator = new OrderNumberGenerator(
            Clock.fixed(Instant.parse("2026-10-12T03:04:05Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void orderNumberFollowsDocumentedFormat() {
        assertThat(generator.next()).matches(FORMAT);
    }

    @Test
    void orderNumberEmbedsTheBusinessDate() {
        assertThat(generator.next()).startsWith("ORD20261012");
    }

    @Test
    void generatorDoesNotProduceDuplicates() {
        var generated = new HashSet<String>();
        IntStream.range(0, 20_000).forEach(i -> {
            assertThat(generated.add(generator.next())).isTrue();
        });
    }

    @Test
    void defaultConstructorUsesSystemClock() {
        assertThat(new OrderNumberGenerator().next()).matches(FORMAT);
    }
}
