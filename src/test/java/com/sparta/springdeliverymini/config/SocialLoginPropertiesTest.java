package com.sparta.springdeliverymini.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// social.* 설정 검증 (V22) — 잘못된 설정은 기동 시점에 실패
class SocialLoginPropertiesTest {

    private static final String CALLBACK = "http://localhost:5173/oauth/callback";

    @Test
    void 키가_둘_다_있으면_켜지고_둘_다_없으면_꺼진다() {
        var properties = new SocialLoginProperties(CALLBACK, Map.of(
                "kakao", new SocialLoginProperties.Client("id", "secret"),
                "google", new SocialLoginProperties.Client("", "")), null);

        assertThat(properties.providers().get("kakao").configured()).isTrue();
        assertThat(properties.providers().get("google").configured()).isFalse();
    }

    @Test
    void 키가_하나만_있으면_설정_실수로_보고_기동을_막는다() {
        assertThatThrownBy(() -> new SocialLoginProperties(CALLBACK,
                Map.of("kakao", new SocialLoginProperties.Client("id", "")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("social.providers.kakao");
    }

    @Test
    void 프론트_콜백_주소는_쿼리_없는_http_절대_주소만_허용한다() {
        for (String invalid : new String[]{"", "/oauth/callback", "javascript:alert(1)",
                "http://localhost:5173/cb?next=x", "http://localhost:5173/cb#x"}) {
            assertThatThrownBy(() -> new SocialLoginProperties(invalid, Map.of(), null))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 콜백_고정_주소가_없으면_요청_기준_주소를_쓴다() {
        assertThat(new SocialLoginProperties(CALLBACK, Map.of(), " ").redirectUriTemplate())
                .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    }

    @Test
    void 콜백_고정_주소가_있으면_그_주소를_앞에_붙이고_끝_슬래시는_뗀다() {
        assertThat(new SocialLoginProperties(CALLBACK, Map.of(), "https://app.example.com/").redirectUriTemplate())
                .isEqualTo("https://app.example.com/login/oauth2/code/{registrationId}");
    }

    @Test
    void 콜백_고정_주소는_경로나_쿼리가_있으면_기동을_막는다() {
        for (String invalid : new String[]{"app.example.com", "ftp://app.example.com",
                "https://app.example.com/api", "https://app.example.com?x=1"}) {
            assertThatThrownBy(() -> new SocialLoginProperties(CALLBACK, Map.of(), invalid))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("social.redirect-base-url");
        }
    }

    @Test
    void 시크릿은_toString에_나오지_않는다() {
        assertThat(new SocialLoginProperties.Client("id", "top-secret").toString()).doesNotContain("top-secret");
    }
}
