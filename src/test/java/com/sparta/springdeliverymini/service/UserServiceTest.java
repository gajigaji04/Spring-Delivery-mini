package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.dto.EmailChangeRequest;
import com.sparta.springdeliverymini.dto.MyInfoResponse;
import com.sparta.springdeliverymini.dto.PasswordChangeRequest;
import com.sparta.springdeliverymini.dto.SignupRequest;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.repository.PasswordResetTokenRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 회원가입 — 고객만 가입(V17, OWNER·ADMIN 400) / 내 정보 조회(#62) — DB 기준 역할·OWNER 매장 id (DB 없이)
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthTokenService authTokenService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private DemoDataProperties demoDataProperties; // 기본 false → 공개 데모 계정 아님
    @Mock private EmailVerificationService emailVerificationService; // 대기 중인 주소 없음(null)

    @InjectMocks
    private UserService userService;

    @Test
    void ADMIN으로는_가입할_수_없다() {
        SignupRequest request = new SignupRequest("admin1", "password123", Role.ADMIN);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    // V17 승인제 — 사장님은 가입 후 전환 신청 + 관리자 승인으로만
    @Test
    void OWNER로는_가입할_수_없다() {
        SignupRequest request = new SignupRequest("owner1", "password123", Role.OWNER);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void role을_생략하면_CUSTOMER로_가입된다() {
        given(userRepository.existsByUsername("customer1")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded");

        userService.signup(new SignupRequest("customer1", "password123", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    private User user(Long id, String username, Role role) {
        User user = new User(username, "encoded-password", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void OWNER의_내_정보에는_매장_id_목록이_포함된다() {
        given(userRepository.findByUsername("owner1")).willReturn(Optional.of(user(1L, "owner1", Role.OWNER)));
        given(storeRepository.findIdsByOwnerId(1L)).willReturn(List.of(3L, 7L));

        MyInfoResponse response = userService.getMyInfo("owner1");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("owner1");
        assertThat(response.role()).isEqualTo(Role.OWNER);
        assertThat(response.storeIds()).containsExactly(3L, 7L);
    }

    @Test
    void CUSTOMER는_매장을_조회하지_않고_빈_목록을_받는다() {
        given(userRepository.findByUsername("customer1")).willReturn(Optional.of(user(2L, "customer1", Role.CUSTOMER)));

        MyInfoResponse response = userService.getMyInfo("customer1");

        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.storeIds()).isEmpty();
        verifyNoInteractions(storeRepository);
    }

    @Test
    void 토큰은_유효하지만_회원이_없으면_401() {
        given(userRepository.findByUsername("deleted")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo("deleted"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== 비밀번호·이메일 변경 (V23) =====

    @Test
    void 공개_데모_계정은_비밀번호를_바꿀_수_없다() {
        User demo = new User("democustomer", "encoded", Role.CUSTOMER);
        given(userRepository.findByUsernameForUpdate("democustomer")).willReturn(Optional.of(demo));
        given(demoDataProperties.isPublicDemoAccount("democustomer")).willReturn(true);

        assertThatThrownBy(() -> userService.changePassword("democustomer",
                new PasswordChangeRequest("password123", "newpassword456"), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.DEMO_ACCOUNT_PROTECTED);
        assertThat(demo.getPassword()).isEqualTo("encoded");
        verifyNoInteractions(authTokenService);
    }

    @Test
    void 공개_데모_계정은_이메일을_바꿀_수_없다() {
        given(userRepository.findByUsernameForUpdate("demoowner"))
                .willReturn(Optional.of(new User("demoowner", "encoded", Role.OWNER)));
        given(demoDataProperties.isPublicDemoAccount("demoowner")).willReturn(true);

        assertThatThrownBy(() -> userService.changeEmail("demoowner",
                new EmailChangeRequest("me@example.com", "password123")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.DEMO_ACCOUNT_PROTECTED);
    }

    @Test
    void 비밀번호가_있는_회원은_현재_비밀번호가_없으면_이메일을_바꿀_수_없다() {
        given(userRepository.findByUsernameForUpdate("customer1"))
                .willReturn(Optional.of(new User("customer1", "encoded", Role.CUSTOMER)));

        assertThatThrownBy(() -> userService.changeEmail("customer1", new EmailChangeRequest("me@example.com", null)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    // ===== 이메일 인증 (V24) — 주소는 인증 링크를 열어야 확정 (EmailVerificationService) =====

    @Test
    void 가입할_때_입력한_이메일은_바로_저장하지_않고_인증_메일을_요청한다() {
        userService.signup(new SignupRequest("customer1", "password123", null, " New@Example.com "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isNull();
        verify(emailVerificationService).requestForNewUser(saved.getValue(), "new@example.com");
    }

    @Test
    void 이메일을_바꾸면_새_주소를_인증할_때까지_기존_주소를_유지한다() {
        User user = verifiedUser("old@example.com");
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);

        userService.changeEmail("customer1", new EmailChangeRequest("new@example.com", "password123"));

        verify(emailVerificationService).request(user, "new@example.com");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void 인증된_지금_주소를_다시_입력하면_진행_중인_변경을_취소하고_메일은_보내지_않는다() {
        User user = verifiedUser("old@example.com");
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);

        userService.changeEmail("customer1", new EmailChangeRequest("OLD@example.com", "password123"));

        verify(emailVerificationService).cancelPending(user.getId());
        verify(emailVerificationService, never()).request(any(), any());
    }

    private User verifiedUser(String email) {
        User user = new User("customer1", "encoded", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        user.verifyEmail(email, LocalDateTime.of(2026, 11, 23, 12, 0));
        given(userRepository.findByUsernameForUpdate("customer1")).willReturn(Optional.of(user));
        given(userRepository.findByUsername("customer1")).willReturn(Optional.of(user));
        return user;
    }
}
