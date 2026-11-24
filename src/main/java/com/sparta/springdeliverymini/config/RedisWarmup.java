package com.sparta.springdeliverymini.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 기동 직후 Redis 연결을 미리 맺어 둠 (prod 프로필)
//
// 왜: Lettuce는 처음 명령을 보낼 때 연결하므로, 그대로 두면 "서버가 깨어난 뒤 첫 결제"가 연결 비용(DNS·TCP·TLS·인증)을 떠안음
//   무료 배포(Render 슬립 + Upstash TLS)에서는 이 비용이 명령 타임아웃을 넘겨 첫 결제만 503이 났음 (application-prod-free.yml)
// 실패해도 기동은 계속 — Redis는 보조 수단이고, 결제는 요청 시점에 다시 연결을 시도해 안 되면 503으로 끝남(데이터는 바꾸지 않음)
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class RedisWarmup {

    private final StringRedisTemplate redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long started = System.nanoTime();
        try {
            redisTemplate.hasKey("warmup"); // 읽기 명령 하나로 연결만 맺음 (값을 쓰지 않음)
            log.info("Redis 연결 예열 완료 ({}ms)", (System.nanoTime() - started) / 1_000_000);
        } catch (DataAccessException e) {
            log.warn("Redis 연결 예열 실패 — 기동은 계속하고 요청 때 다시 연결 ({}ms): {}",
                    (System.nanoTime() - started) / 1_000_000, e.getMessage());
        }
    }
}
