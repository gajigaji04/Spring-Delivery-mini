package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.maintenance.DemoDataSeeder;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

// 데모 데이터 초기화 (관리자 API) — 방문자가 확정·지급·승인·주문한 결과를 지우고 처음 상태로 (docs/operations.md 9-6)
//
// - 데모 관리자(체험 로그인)도 호출 가능: 면접관이 직접 처음 상태로 되돌리고 시연할 수 있도록. 지우는 범위는 데모 매장·신청자뿐
// - 데모가 꺼진 배포(운영 기본값)는 시더 빈이 없어 404 → 운영 데이터는 이 API로 지울 수 없음
// - 1분 간격 제한(429): 초기화는 여러 표를 지우고 다시 만드는 무거운 작업 — 버튼 연타·반복 호출로 DB를 바쁘게 하지 않도록
//   간격은 인스턴스 메모리 기준 (무료 배포는 인스턴스 1개, 재시작하면 처음부터)
// 트랜잭션은 시더가 단계별로 엶 (정산 집계는 매장마다 따로 커밋해야 하므로 여기서 묶지 않음)
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataResetService {

    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private final ObjectProvider<DemoDataSeeder> seeder; // demo-data.enabled=true일 때만 빈이 있음
    private final UserRepository userRepository;
    private final Clock clock;
    private final AtomicReference<LocalDateTime> lastResetAt = new AtomicReference<>();

    public LocalDateTime reset(String username) {
        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));
        if (admin.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "관리자만 데모 데이터를 초기화할 수 있습니다.");
        }
        DemoDataSeeder demoDataSeeder = seeder.getIfAvailable();
        if (demoDataSeeder == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.DEMO_DATA_DISABLED, "데모 데이터가 꺼진 배포입니다.");
        }

        // 간격 확인과 기록을 한 번에 (동시에 두 요청이 와도 하나만 통과)
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime previous = lastResetAt.get();
        if ((previous != null && previous.plus(MIN_INTERVAL).isAfter(now)) || !lastResetAt.compareAndSet(previous, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.DEMO_RESET_TOO_FREQUENT,
                    "방금 초기화했습니다. 1분 뒤에 다시 시도해 주세요.");
        }

        demoDataSeeder.reset();
        log.info("데모 데이터 초기화 actorId={}", admin.getId());
        return now;
    }
}
