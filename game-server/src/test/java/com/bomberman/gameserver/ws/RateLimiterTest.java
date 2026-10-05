package com.bomberman.gameserver.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void aceptaHastaElMaximoPorSegundoYLuegoRechaza() {
        RateLimiter limiter = new RateLimiter(3, 1000);
        long t = 10_000;

        assertThat(limiter.tryAcquire(t)).isTrue();
        assertThat(limiter.tryAcquire(t + 10)).isTrue();
        assertThat(limiter.tryAcquire(t + 20)).isTrue();
        assertThat(limiter.tryAcquire(t + 30)).isFalse();
        assertThat(limiter.getTotalRejected()).isEqualTo(1);

        // Un segundo después, la ventana se reinicia
        assertThat(limiter.tryAcquire(t + 1000)).isTrue();
    }
}
