package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.dto.PageQuery;
import com.sparta.springdeliverymini.entity.OwnerApplication;
import com.sparta.springdeliverymini.entity.OwnerApplicationStatus;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.OwnerApplicationRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 사장님 전환 신청·승인의 권한(DB 역할 기준)과 진행 중 주문 규칙 (DB 없이)
@ExtendWith(MockitoExtension.class)
class OwnerApplicationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T01:00:00Z"), SEOUL);

    @Mock private OwnerApplicationRepository ownerApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private OwnerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OwnerApplicationService(ownerApplicationRepository, userRepository, orderRepository, CLOCK,
                DemoAdminScopes.disabled());
    }

    private User user(Long id, String username, Role role) {
        User user = new User(username, "pw", role);
        ReflectionTestUtils.setField(user, "id", id);
        given(userRepository.findByUsername(username)).willReturn(Optional.of(user));
        return user;
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ApiException.class).extracting("status").isEqualTo(status);
    }

    @Test
    void 고객은_신청할_수_있고_신청_시각은_주입된_Clock_기준이다() {
        User customer = user(1L, "cus", Role.CUSTOMER);

        var response = service.apply("cus", "치킨집");

        assertThat(response.status()).isEqualTo(OwnerApplicationStatus.REQUESTED);
        assertThat(response.userId()).isEqualTo(customer.getId());
        assertThat(response.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 10, 0));
        verify(ownerApplicationRepository).saveAndFlush(any(OwnerApplication.class));
    }

    @Test
    void 토큰_역할과_무관하게_DB_역할이_고객이_아니면_신청은_403() {
        user(1L, "own", Role.OWNER);
        user(2L, "adm", Role.ADMIN);

        assertStatus(catchThrowable(() -> service.apply("own", "치킨집")), HttpStatus.FORBIDDEN);
        assertStatus(catchThrowable(() -> service.apply("adm", "치킨집")), HttpStatus.FORBIDDEN);
        verify(ownerApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 대기_중인_신청이_있으면_다시_신청할_수_없다() {
        user(1L, "cus", Role.CUSTOMER);
        given(ownerApplicationRepository.existsByUserIdAndStatus(1L, OwnerApplicationStatus.REQUESTED)).willReturn(true);

        assertStatus(catchThrowable(() -> service.apply("cus", "치킨집")), HttpStatus.CONFLICT);
        verify(ownerApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 진행_중인_주문이_있으면_신청할_수_없다() {
        user(1L, "cus", Role.CUSTOMER);
        given(orderRepository.existsByUserIdAndStatusIn(1L, OwnerApplicationService.IN_PROGRESS_ORDER_STATUSES))
                .willReturn(true);

        assertStatus(catchThrowable(() -> service.apply("cus", "치킨집")), HttpStatus.CONFLICT);
        verify(ownerApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 승인_시점에_진행_중인_주문이_생겼으면_승인하지_않는다() {
        User admin = user(9L, "adm", Role.ADMIN);
        User customer = new User("cus", "pw", Role.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", 1L);
        OwnerApplication application = new OwnerApplication(customer, "치킨집", LocalDateTime.now(CLOCK));
        given(ownerApplicationRepository.findByIdForUpdate(5L)).willReturn(Optional.of(application));
        given(orderRepository.existsByUserIdAndStatusIn(1L, OwnerApplicationService.IN_PROGRESS_ORDER_STATUSES))
                .willReturn(true);

        assertStatus(catchThrowable(() -> service.approve(5L, admin.getUsername())), HttpStatus.CONFLICT);
        assertThat(application.getStatus()).isEqualTo(OwnerApplicationStatus.REQUESTED);
        assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void 관리자가_아니면_목록_승인_거절_모두_403이고_신청을_조회하지_않는다() {
        user(1L, "own", Role.OWNER);

        assertStatus(catchThrowable(() -> service.approve(5L, "own")), HttpStatus.FORBIDDEN);
        assertStatus(catchThrowable(() -> service.reject(5L, "사유", "own")), HttpStatus.FORBIDDEN);
        assertStatus(catchThrowable(() -> service.getApplications(
                OwnerApplicationStatus.REQUESTED, com.sparta.springdeliverymini.dto.PageQuery.of(0, 20), "own")),
                HttpStatus.FORBIDDEN);
        verify(ownerApplicationRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void 데모_관리자_목록은_데모_회원_신청만_조회하고_일반_관리자는_전체를_조회한다() {
        // 데모 관리자에게 일반 회원(소셜 가입 등)의 신청이 안 보이는 것은 의도 — 화면에서 안내 문구로 알림
        DemoDataProperties properties = new DemoDataProperties(true, "demo-pass-123", null, null, null, null, null);
        OwnerApplicationService demoService = new OwnerApplicationService(ownerApplicationRepository, userRepository,
                orderRepository, CLOCK, new DemoAdminScope(properties, userRepository, mock(StoreRepository.class)));
        user(9L, properties.adminUsername(), Role.ADMIN);
        user(10L, "adm", Role.ADMIN);
        PageQuery pageQuery = PageQuery.of(0, 20);
        given(ownerApplicationRepository.findAllByStatusAndUserUsernameIn(any(), any(), any()))
                .willReturn(new SliceImpl<>(List.of()));
        given(ownerApplicationRepository.findAllByStatus(any(), any())).willReturn(new SliceImpl<>(List.of()));

        demoService.getApplications(OwnerApplicationStatus.REQUESTED, pageQuery, properties.adminUsername());
        verify(ownerApplicationRepository).findAllByStatusAndUserUsernameIn(
                OwnerApplicationStatus.REQUESTED, properties.demoUsernames(), pageQuery.toPageable());
        verify(ownerApplicationRepository, never()).findAllByStatus(any(), any());

        demoService.getApplications(OwnerApplicationStatus.REQUESTED, pageQuery, "adm");
        verify(ownerApplicationRepository).findAllByStatus(OwnerApplicationStatus.REQUESTED, pageQuery.toPageable());
    }

    @Test
    void 없는_신청을_처리하면_404() {
        user(9L, "adm", Role.ADMIN);
        given(ownerApplicationRepository.findByIdForUpdate(5L)).willReturn(Optional.empty());

        assertStatus(catchThrowable(() -> service.approve(5L, "adm")), HttpStatus.NOT_FOUND);
    }

    @Test
    void 내_신청이_없으면_404() {
        user(1L, "cus", Role.CUSTOMER);
        given(ownerApplicationRepository.findFirstByUserIdOrderByIdDesc(1L)).willReturn(Optional.empty());

        assertStatus(catchThrowable(() -> service.getMyLatest("cus")), HttpStatus.NOT_FOUND);
    }

    @Test
    void 토큰은_유효하지만_회원이_없으면_401() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply("ghost", "치킨집"))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
