package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.entity.Store;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 데모 관리자 범위 — 배포 데모에서 방문자가 비밀번호 없이 들어오는 관리자(demo-data.admin-username)는
// 데모 회원·데모 사장님 매장의 데이터만 보고 처리함 (docs/operations.md 9-6)
//
// 왜: 관리자 권한을 누구나 쓰게 열면, 다른 방문자가 가입·주문·신청한 데이터(아이디·상호 등)까지 보이고 바뀔 수 있음
// - 목록: 데모 범위만 걸러서 보여 줌 (조회 자체는 막지 않음 — 시연 화면이 비지 않도록)
// - 단건 처리(확정·지급·환불·승인·수수료율): 범위 밖이면 403 DEMO_ADMIN_SCOPE
// - Outbox: 시스템 전체 이벤트(다른 회원 주문·결제 내용)라 데모 관리자는 전부 403
// - 데모가 꺼져 있거나 일반 관리자면 아무것도 제한하지 않음
@Component
@RequiredArgsConstructor
public class DemoAdminScope {

    private final DemoDataProperties properties;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    public boolean isDemoAdmin(User admin) {
        return properties.isDemoAdmin(admin.getUsername());
    }

    // 데모 사장님의 매장 ID (데모 관리자가 볼 수 있는 매장) — 데모 사장님이 없으면 빈 집합
    public Set<Long> demoStoreIds() {
        return userRepository.findByUsername(properties.ownerUsername())
                .map(owner -> Set.copyOf(storeRepository.findIdsByOwnerId(owner.getId())))
                .orElse(Set.of());
    }

    public List<String> demoUsernames() {
        return properties.demoUsernames();
    }

    // 매장이 데모 범위 밖이면 403 (데모 관리자일 때만)
    public void requireStore(User admin, Long storeId) {
        // Set.copyOf로 만든 집합은 contains(null)에서 NPE → 매장 미지정은 먼저 범위 밖으로 처리
        if (isDemoAdmin(admin) && (storeId == null || !demoStoreIds().contains(storeId))) {
            throw outOfScope("데모 관리자는 데모 매장(데모 치킨·데모 분식)의 데이터만 처리할 수 있습니다.");
        }
    }

    public void requireStore(User admin, Store store) {
        requireStore(admin, store.getId());
    }

    // 회원이 데모 범위 밖이면 403 (데모 관리자일 때만)
    public void requireUser(User admin, User target) {
        if (isDemoAdmin(admin) && !demoUsernames().contains(target.getUsername())) {
            throw outOfScope("데모 관리자는 데모 회원의 신청만 처리할 수 있습니다.");
        }
    }

    // 데모 관리자에게 열지 않는 기능 — reason은 완성된 안내 문장
    //   (기능 이름에 "을(를)"을 붙여 만들면 "관리을(를)"처럼 어색해져 호출하는 쪽이 문장 전체를 넘김)
    public void deny(User admin, String reason) {
        if (isDemoAdmin(admin)) {
            throw outOfScope(reason);
        }
    }

    // 공개 데모 계정(방문자 모두가 같이 쓰는 계정)에 열지 않는 기능 — 소셜 연결·사장님 전환 신청 등
    // 한 방문자가 소셜 계정을 붙이거나 역할을 바꾸면 다음 방문자의 데모가 깨지므로 서버에서 막음 (화면 숨김은 편의)
    // reason은 완성된 안내 문장 (deny와 같은 이유)
    public void rejectPublicDemoAccount(User user, String reason) {
        if (properties.isPublicDemoAccount(user.getUsername())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.DEMO_ACCOUNT_PROTECTED, reason);
        }
    }

    // 목록에서 데모 매장 것만 남김 (데모 관리자가 아니면 그대로)
    public <T> List<T> filterByStore(User admin, List<T> items, Function<T, Long> storeIdOf) {
        if (!isDemoAdmin(admin)) {
            return items;
        }
        Set<Long> storeIds = demoStoreIds();
        return items.stream().filter(item -> storeIds.contains(storeIdOf.apply(item))).collect(Collectors.toList());
    }

    private ApiException outOfScope(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.DEMO_ADMIN_SCOPE, message);
    }
}
