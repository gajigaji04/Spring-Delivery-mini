package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.TestcontainersConfiguration;
import com.sparta.springdeliverymini.entity.PasswordResetToken;
import com.sparta.springdeliverymini.entity.RefreshTokenRevokeReason;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.jwt.JwtProvider;
import com.sparta.springdeliverymini.mail.EmailMessage;
import com.sparta.springdeliverymini.mail.EmailSender;
import com.sparta.springdeliverymini.repository.PasswordResetTokenRepository;
import com.sparta.springdeliverymini.repository.RefreshTokenRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import com.sparta.springdeliverymini.service.SettlementIntegrationTest.MutableClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 비밀번호 찾기·변경, 이메일 등록 (V23)을 실제 PostgreSQL + 보안 필터까지 통과시켜 검증
// - 재설정: 가입 여부와 관계없이 202, 메일 링크(#token=)로 1회만 재설정, 재설정하면 모든 로그인 폐기
// - 변경: 현재 비밀번호 확인, 지금 기기 외 로그인 폐기, 소셜 전용 회원은 첫 설정
// 메일은 가짜 발송기로 받음 (발송은 커밋 뒤 전용 스레드라 도착을 기다림)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SettlementIntegrationTest.MutableClockConfig.class,
        PasswordIntegrationTest.FakeMailConfig.class})
class PasswordIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final String COOKIE = "refresh_token";
    private static final Pattern LINK_TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeMailConfig {
        // MailConfig의 log 발송기 대신 주입되도록 @Primary
        @Bean
        @Primary
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }
    }

    static class FakeEmailSender implements EmailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private FakeEmailSender mail;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        mail.sent.clear();
    }

    @AfterEach
    void tearDown() {
        // FK 순서대로 삭제: password_reset_tokens·refresh_tokens → users
        passwordResetTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ===== 도우미 =====

    private void signup(String username, String password, String email) throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\"}"
                                .formatted(username, password, email)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn();
    }

    private String refreshCookie(String username, String password) throws Exception {
        MvcResult result = login(username, password);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(COOKIE).getValue();
    }

    private MvcResult requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/users/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andReturn();
    }

    private String awaitResetToken() {
        await().atMost(Duration.ofSeconds(5)).until(() -> !mail.sent.isEmpty());
        Matcher matcher = LINK_TOKEN.matcher(mail.sent.getLast().text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MvcResult confirmReset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)))
                .andReturn();
    }

    private String bearer(String username) {
        return "Bearer " + jwtProvider.createToken(username, Role.CUSTOMER);
    }

    // ===== 비밀번호 찾기 =====

    @Test
    void 메일_링크로_비밀번호를_재설정하면_새_비밀번호로만_로그인되고_기존_로그인은_모두_끊긴다() throws Exception {
        signup("customer1", "password123", "Customer1@Example.com");
        String loggedIn = refreshCookie("customer1", "password123");

        // 대소문자가 달라도 같은 주소 (저장·조회 모두 소문자)
        assertThat(requestReset("customer1@example.COM").getResponse().getStatus()).isEqualTo(202);
        String token = awaitResetToken();
        assertThat(mail.sent.getLast().to()).isEqualTo("customer1@example.com");
        assertThat(mail.sent.getLast().text()).contains("http://localhost:5173/password-reset#token=");
        // DB에는 원문이 아니라 해시만
        assertThat(passwordResetTokenRepository.findAll()).extracting(PasswordResetToken::getTokenHash)
                .doesNotContain(token);

        assertThat(confirmReset(token, "newpassword456").getResponse().getStatus()).isEqualTo(204);

        assertThat(login("customer1", "password123").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("customer1", "newpassword456").getResponse().getStatus()).isEqualTo(200);
        mockMvc.perform(post("/api/users/refresh").cookie(new Cookie(COOKIE, loggedIn)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        assertThat(refreshTokenRepository.findAll())
                .anyMatch(t -> t.getRevokeReason() == RefreshTokenRevokeReason.PASSWORD_CHANGED);

        // 같은 링크는 한 번만
        mockMvc.perform(post("/api/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"another789\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void 가입되지_않은_이메일도_같은_202를_받고_메일은_보내지_않는다() throws Exception {
        signup("customer1", "password123", "customer1@example.com");

        assertThat(requestReset("nobody@example.com").getResponse().getStatus()).isEqualTo(202);

        // 발송은 비동기 — 가입된 주소로 한 통 보낸 뒤 그 한 통만 있는지 확인 (없는 주소 메일이 늦게 도착하지 않았음)
        requestReset("customer1@example.com");
        awaitResetToken();
        assertThat(mail.sent).hasSize(1);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void 재요청_간격_안에는_다시_보내지_않고_간격이_지나면_이전_링크는_무효가_된다() throws Exception {
        signup("customer1", "password123", "customer1@example.com");
        requestReset("customer1@example.com");
        String first = awaitResetToken();

        assertThat(requestReset("customer1@example.com").getResponse().getStatus()).isEqualTo(202);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);

        clock.set(NOW.plusMinutes(2));
        mail.sent.clear();
        requestReset("customer1@example.com");
        String second = awaitResetToken();

        assertThat(confirmReset(first, "newpassword456").getResponse().getStatus()).isEqualTo(400);
        assertThat(confirmReset(second, "newpassword456").getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void 만료된_링크로는_재설정할_수_없다() throws Exception {
        signup("customer1", "password123", "customer1@example.com");
        requestReset("customer1@example.com");
        String token = awaitResetToken();

        clock.set(NOW.plusMinutes(30));

        mockMvc.perform(post("/api/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"newpassword456\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        assertThat(login("customer1", "password123").getResponse().getStatus()).isEqualTo(200);
    }

    // ===== 비밀번호 변경 =====

    @Test
    void 비밀번호를_바꾸면_지금_기기의_로그인은_유지되고_다른_기기의_로그인은_끊긴다() throws Exception {
        signup("customer1", "password123", "customer1@example.com");
        String deviceA = refreshCookie("customer1", "password123");
        String deviceB = refreshCookie("customer1", "password123");

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer1"))
                        .cookie(new Cookie(COOKIE, deviceA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/refresh").cookie(new Cookie(COOKIE, deviceA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/refresh").cookie(new Cookie(COOKIE, deviceB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        assertThat(login("customer1", "newpassword456").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 현재_비밀번호가_틀리면_400이고_비밀번호는_그대로다() throws Exception {
        signup("customer1", "password123", "customer1@example.com");

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        assertThat(login("customer1", "password123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 소셜_전용_회원은_현재_비밀번호_없이_비밀번호를_설정해_아이디로_로그인할_수_있다() throws Exception {
        userRepository.save(User.socialCustomer("kakao_1234"));

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer("kakao_1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isNoContent());

        assertThat(login("kakao_1234", "newpassword456").getResponse().getStatus()).isEqualTo(200);
    }

    // ===== 이메일 =====

    @Test
    void 다른_회원이_쓰는_이메일은_가입과_변경_모두_409다() throws Exception {
        signup("customer1", "password123", "same@example.com");
        userRepository.save(new User("customer2", passwordEncoder.encode("password123"), Role.CUSTOMER));

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer3\",\"password\":\"password123\",\"email\":\"SAME@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        mockMvc.perform(patch("/api/users/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"same@example.com\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void 이메일을_등록하면_내_정보에_보이고_그_주소로_비밀번호를_찾을_수_있다() throws Exception {
        userRepository.save(new User("customer1", passwordEncoder.encode("password123"), Role.CUSTOMER));

        mockMvc.perform(patch("/api/users/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"New@Example.com\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.demoAccount").value(false));

        requestReset("new@example.com");
        assertThat(awaitResetToken()).hasSize(43);
    }
}
