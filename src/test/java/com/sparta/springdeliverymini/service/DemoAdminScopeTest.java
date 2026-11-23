package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

// 데모 관리자 범위 — 데모 관리자만 데모 사장님 매장·데모 회원으로 좁히고, 일반 관리자·데모 꺼짐은 그대로
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoAdminScopeTest {

    private static final DemoDataProperties ENABLED =
            new DemoDataProperties(true, "demo-pass-123", null, null, null, null, null);

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;

    private final User demoAdmin = new User("demoadmin", null, Role.ADMIN);
    private final User realAdmin = new User("admin1", "pw", Role.ADMIN);

    @BeforeEach
    void setUp() {
        User owner = new User("demoowner", "pw", Role.OWNER);
        ReflectionTestUtils.setField(owner, "id", 5L);
        given(userRepository.findByUsername("demoowner")).willReturn(Optional.of(owner));
        given(storeRepository.findIdsByOwnerId(5L)).willReturn(List.of(1L, 2L));
    }

    @Test
    void 데모_관리자는_데모_매장만_처리하고_다른_매장이나_매장_미지정은_403() {
        DemoAdminScope scope = new DemoAdminScope(ENABLED, userRepository, storeRepository);

        assertThatCode(() -> scope.requireStore(demoAdmin, 1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireStore(demoAdmin, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.DEMO_ADMIN_SCOPE);
                });
        assertThatThrownBy(() -> scope.requireStore(demoAdmin, (Long) null)).isInstanceOf(ApiException.class);
    }

    @Test
    void 데모_관리자_목록은_데모_매장_것만_남긴다() {
        DemoAdminScope scope = new DemoAdminScope(ENABLED, userRepository, storeRepository);

        assertThat(scope.filterByStore(demoAdmin, List.of(1L, 2L, 3L), id -> id)).containsExactly(1L, 2L);
        assertThat(scope.filterByStore(realAdmin, List.of(1L, 2L, 3L), id -> id)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 데모_관리자는_데모_회원의_신청만_처리하고_Outbox는_쓸_수_없다() {
        DemoAdminScope scope = new DemoAdminScope(ENABLED, userRepository, storeRepository);

        assertThatCode(() -> scope.requireUser(demoAdmin, new User("demoapplicant", null, Role.CUSTOMER)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireUser(demoAdmin, new User("visitor1", "pw", Role.CUSTOMER)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> scope.deny(demoAdmin, "Outbox 이벤트 관리")).isInstanceOf(ApiException.class);
    }

    @Test
    void 일반_관리자나_데모가_꺼진_배포의_같은_아이디는_제한하지_않는다() {
        DemoAdminScope enabled = new DemoAdminScope(ENABLED, userRepository, storeRepository);
        DemoAdminScope disabled = new DemoAdminScope(
                new DemoDataProperties(false, null, null, null, null, null, null), userRepository, storeRepository);

        assertThatCode(() -> enabled.requireStore(realAdmin, 3L)).doesNotThrowAnyException();
        assertThatCode(() -> enabled.deny(realAdmin, "Outbox 이벤트 관리")).doesNotThrowAnyException();
        assertThatCode(() -> disabled.requireStore(demoAdmin, 3L)).doesNotThrowAnyException();
        assertThatCode(() -> disabled.requireUser(demoAdmin, new User("visitor1", "pw", Role.CUSTOMER)))
                .doesNotThrowAnyException();
    }
}
