package com.sparta.springdeliverymini.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V24(이메일 인증)가 V23 이메일이 있는 DB에서도 안전하게 적용되는지 검증
// - 기존 이메일은 그대로, 인증 시각은 NULL(미인증) → 기존 회원도 인증해야 비밀번호 찾기 가능
// - 주소 없는 인증 시각·대문자 주소·만료가 발급보다 이른 토큰은 DB가 거부
class EmailVerificationMigrationTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 23, 12, 0);

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        postgres.start();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        // 1. V23까지만 적용 + 확인 없이 이메일을 등록한 기존 회원, 이메일 없는 회원
        flyway(dataSource, "23").migrate();
        jdbc.update("INSERT INTO users (id, password, role, username, email) "
                + "VALUES (1, 'pw', 'CUSTOMER', 'customer1', 'old@example.com')");
        jdbc.update("INSERT INTO users (id, password, role, username) VALUES (2, 'pw', 'CUSTOMER', 'customer2')");

        // 2. 최신 버전까지 적용
        flyway(dataSource, "latest").migrate();
    }

    @AfterAll
    static void tearDown() {
        postgres.stop();
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    @Test
    void 기존_이메일은_그대로_남고_미인증_상태가_된다() {
        Map<String, Object> row = jdbc.queryForMap("SELECT email, email_verified_at FROM users WHERE id = 1");
        assertThat(row.get("email")).isEqualTo("old@example.com");
        assertThat(row.get("email_verified_at")).isNull();
    }

    @Test
    void 주소_없이_인증_시각만_있는_회원은_DB가_거부한다() {
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET email_verified_at = ? WHERE id = 2", AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 인증_토큰은_소문자_주소와_발급보다_늦은_만료만_허용하고_해시는_중복될_수_없다() {
        String insert = "INSERT INTO email_verification_tokens (user_id, email, token_hash, created_at, expires_at) "
                + "VALUES (2, ?, ?, ?, ?)";
        jdbc.update(insert, "new@example.com", "a".repeat(64), AT, AT.plusHours(24));

        assertThatThrownBy(() -> jdbc.update(insert, "New@example.com", "b".repeat(64), AT, AT.plusHours(24)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "new@example.com", "c".repeat(64), AT, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "new@example.com", "a".repeat(64), AT, AT.plusHours(24)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
