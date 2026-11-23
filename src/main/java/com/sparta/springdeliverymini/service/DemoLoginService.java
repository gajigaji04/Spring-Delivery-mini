package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.dto.DemoAccountResponse;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 배포 데모 "체험하기" (docs/operations.md 9-6, docs/api.md 데모 체험 장)
//
// - 고객·사장님: 공개 데모 비밀번호를 내려 주고 로그인 화면이 아이디·비밀번호를 채움 → 일반 로그인 API 그대로
// - 관리자: 비밀번호를 프론트(빌드 결과·응답)에 두지 않음 → 이 서비스가 비밀번호 없이 데모 관리자로 토큰을 발급
//   대신 데모 관리자는 데모 회원·매장 데이터만 다룸 (DemoAdminScope), 비밀번호·이메일 변경 불가 (UserService)
// - demo-data.enabled=false(운영 기본값)면 목록은 빈 배열, 관리자 체험 로그인은 404 → 데모가 아닌 배포에서는 열리지 않음
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DemoLoginService {

    private final DemoDataProperties properties;
    private final UserRepository userRepository;
    private final AuthTokenService authTokenService;

    // 로그인 화면 체험 버튼 목록 — 고객·사장님은 공개 비밀번호 포함, 관리자는 비밀번호 없음(체험 로그인 API 사용)
    public List<DemoAccountResponse> getAccounts() {
        if (!properties.enabled()) {
            return List.of();
        }
        return List.of(
                new DemoAccountResponse(Role.CUSTOMER, properties.customerUsername(), properties.password()),
                new DemoAccountResponse(Role.OWNER, properties.ownerUsername(), properties.password()),
                new DemoAccountResponse(Role.ADMIN, properties.adminUsername(), null));
    }

    // 관리자 체험 로그인 — 일반 로그인과 같은 토큰(Access + Refresh 계열)
    // 쓰기 트랜잭션: Refresh Token 행을 저장함
    @Transactional
    public IssuedTokens loginAsDemoAdmin() {
        if (!properties.enabled()) {
            throw unavailable();
        }
        // 시드 전(첫 기동 중)이거나 같은 아이디가 관리자가 아니면 체험 불가 — 다른 역할 회원에게 관리자 토큰을 주지 않도록
        User admin = userRepository.findByUsername(properties.adminUsername())
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(this::unavailable);
        log.info("데모 관리자 체험 로그인 userId={}", admin.getId());
        return authTokenService.issue(admin);
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.DEMO_LOGIN_UNAVAILABLE, "체험 로그인을 사용할 수 없습니다.");
    }
}
