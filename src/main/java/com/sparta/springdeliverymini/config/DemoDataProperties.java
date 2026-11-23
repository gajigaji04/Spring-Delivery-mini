package com.sparta.springdeliverymini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

// application.yml의 demo-data.* — 배포 데모 데이터 (maintenance.DemoDataSeeder)
// enabled: 기동 시 데모 회원·매장·메뉴·주문을 만들지 (기본 false, 무료 배포에서 DEMO_DATA_ENABLED=true)
// password: 공개 데모 계정(사장님·고객) 비밀번호 — 로그인 화면 버튼(VITE_DEMO_ACCOUNTS)에 그대로 보이는 값
// admin-password: 관리자 비밀번호 — 공개하지 않음. 비우면 관리자는 로그인할 수 없는 승인 기록용 계정으로만 생성
//
// 공개 데모 계정은 방문자 누구나 쓰므로 비밀번호·이메일을 바꿀 수 없음 (UserService — 한 사람이 바꾸면 데모가 깨짐)
@ConfigurationProperties(prefix = "demo-data")
public record DemoDataProperties(boolean enabled, String password, String adminUsername, String adminPassword,
                                 String ownerUsername, String customerUsername) {

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
        if (enabled && (!StringUtils.hasText(password) || password.length() < 8)) {
            throw new IllegalArgumentException(
                    "demo-data.enabled=true이면 demo-data.password(DEMO_PASSWORD, 8자 이상)가 필요합니다.");
        }
        if (enabled && StringUtils.hasText(adminPassword) && adminPassword.length() < 8) {
            throw new IllegalArgumentException("demo-data.admin-password(DEMO_ADMIN_PASSWORD)는 8자 이상이어야 합니다.");
        }
    }

    // 방문자에게 공개된 데모 계정인지 — 데모가 꺼져 있으면 같은 아이디의 일반 회원을 막지 않음
    public boolean isPublicDemoAccount(String username) {
        return enabled && (ownerUsername.equals(username) || customerUsername.equals(username));
    }

    // 비밀번호가 로그에 남지 않도록
    @Override
    public String toString() {
        return "DemoDataProperties[enabled=" + enabled + ", ownerUsername=" + ownerUsername
                + ", customerUsername=" + customerUsername + ", adminUsername=" + adminUsername + "]";
    }
}
