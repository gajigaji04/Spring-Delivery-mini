package com.sparta.springdeliverymini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

// application.yml의 demo-data.* — 배포 데모 데이터 (maintenance.DemoDataSeeder)
// enabled: 기동 시 데모 회원·매장·메뉴·주문을 만들지 (기본 false, 무료 배포에서 DEMO_DATA_ENABLED=true)
// password: 공개 데모 계정(사장님·고객) 비밀번호 — 로그인 화면 "체험하기" 버튼(GET /api/users/demo-accounts)이 채우는 값
// admin-password: 관리자 비밀번호 — 공개하지 않음(운영자 확인용, 선택). 방문자는 비밀번호 없이 데모 관리자 로그인 API로 들어옴
// applicant-username: 승인 대기 사장님 전환 신청을 가진 데모 회원 (관리자 승인 시연용, 비밀번호 없음)
//
// 데모 계정은 방문자 누구나 쓰므로 비밀번호·이메일을 바꿀 수 없음 (UserService — 한 사람이 바꾸면 데모가 깨짐)
// 데모 관리자는 데모 회원·매장 데이터만 보고 처리함 (service.DemoAdminScope)
@ConfigurationProperties(prefix = "demo-data")
public record DemoDataProperties(boolean enabled, String password, String adminUsername, String adminPassword,
                                 String ownerUsername, String customerUsername, String applicantUsername) {

    public DemoDataProperties {
        if (!StringUtils.hasText(adminUsername)) {
            adminUsername = "demoadmin";
        }
        if (!StringUtils.hasText(ownerUsername)) {
            ownerUsername = "demoowner";
        }
        if (!StringUtils.hasText(customerUsername)) {
            customerUsername = "democustomer";
        }
        if (!StringUtils.hasText(applicantUsername)) {
            applicantUsername = "demoapplicant";
        }
        if (enabled && (!StringUtils.hasText(password) || password.length() < 8)) {
            throw new IllegalArgumentException(
                    "demo-data.enabled=true이면 demo-data.password(DEMO_PASSWORD, 8자 이상)가 필요합니다.");
        }
        if (enabled && StringUtils.hasText(adminPassword) && adminPassword.length() < 8) {
            throw new IllegalArgumentException("demo-data.admin-password(DEMO_ADMIN_PASSWORD)는 8자 이상이어야 합니다.");
        }
    }

    // 방문자에게 공개된 데모 계정인지 (관리자 포함 — 데모 관리자 로그인 API로 누구나 들어옴)
    // 데모가 꺼져 있으면 같은 아이디의 일반 회원을 막지 않음
    public boolean isPublicDemoAccount(String username) {
        return enabled && (ownerUsername.equals(username) || customerUsername.equals(username)
                || adminUsername.equals(username) || applicantUsername.equals(username));
    }

    // 데모 관리자인지 — 데모 데이터만 다루도록 범위를 좁힐 대상
    public boolean isDemoAdmin(String username) {
        return enabled && adminUsername.equals(username);
    }

    // 데모 관리자가 볼 수 있는 회원 (사장님 전환 신청 목록·처리 범위)
    public List<String> demoUsernames() {
        return List.of(adminUsername, ownerUsername, customerUsername, applicantUsername);
    }

    // 비밀번호가 로그에 남지 않도록
    @Override
    public String toString() {
        return "DemoDataProperties[enabled=" + enabled + ", ownerUsername=" + ownerUsername
                + ", customerUsername=" + customerUsername + ", adminUsername=" + adminUsername
                + ", applicantUsername=" + applicantUsername + "]";
    }
}
