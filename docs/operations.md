# 운영 프로필 (#54) · CORS (#61) · 배포 (#86)

[← README](../README.md)

운영 배포용 `prod` 프로필(`src/main/resources/application-prod.yml`)의 설정과 근거, 점검 결과.

## 1. 작업 전 점검 (기존 구성)

| 항목 | 기존 상태 | 판단 |
|---|---|---|
| 프로필 | `local`(기본) / `dev`(docker-compose) / `test` / `dlt-reprocess`(CLI). 운영 프로필 없음 | 기본 설정(application.yml)이 운영 기준이라 별도 프로필 없이도 Swagger 비활성·health만 노출 |
| Swagger | 기본 비활성, local·dev에서만 켬 | 유지. 다만 환경변수(`SPRINGDOC_*`)로 켜도 막을 장치가 없음 |
| Actuator | 기본 health만 노출(상세 숨김), local은 metrics·prometheus, dev는 비공개 관리 포트(8081)에 prometheus | 기본 설정으로 운영하면 prometheus를 수집할 수 없음. 노출 목록(exposure)만으로 막고 있어 설정 실수 하나로 env 등이 열릴 수 있음 |
| 보안 규칙 | `SecurityConfig`: `/actuator/health`·`/actuator/prometheus` 공개, 나머지 `/actuator/**`는 ADMIN, Swagger 경로 공개(비활성이면 404) | 변경 없음 |
| 비밀 값 | DB 계정·`JWT_SECRET`은 환경변수 필수(기본값 없음). DB URL은 프로필별, Redis·Kafka 주소는 기본값(localhost) | 운영에서 Redis·Kafka 주소를 빠뜨리면 localhost로 접속을 시도 → 기동은 되고 결제 멱등성·이벤트 발행이 뒤늦게 실패 |
| 배포 구성 | `Dockerfile`(JRE, 비 root 사용자), `docker-compose.yml`은 dev 프로필 | 이미지는 그대로 쓰고 `SPRING_PROFILES_ACTIVE=prod`만 지정. compose는 개발용이라 변경하지 않음 |

## 2. prod 프로필 설정

| 항목 | 설정 | 이유 |
|---|---|---|
| 접속 정보 | `DB_URL`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS` 환경변수 (기본값 없음) | 개발용 기본값(localhost)으로 조용히 뜨지 않도록 |
| 필수 값 점검 | `config.ProdProfileGuard` — `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`·`JWT_SECRET`·`REDIS_HOST`·`KAFKA_BOOTSTRAP_SERVERS`·`CORS_ALLOWED_ORIGINS`(#61) 중 비어 있는 것이 있으면 기동 실패 | Redis·Kafka는 처음 쓸 때 접속하고, CORS 출처가 비어도 서버는 정상 기동(브라우저 요청만 403)하므로 배포 시점에 드러나지 않음. 메시지에는 변수 이름만 넣고 값은 넣지 않음. 빈이 아닌 `EnvironmentPostProcessor`로 등록해 빈 생성 전에 검사 → `DB_URL`이 없어도 DataSource 오류보다 먼저 빠진 변수 목록이 나옴(#61) |
| CORS 허용 출처 | `CORS_ALLOWED_ORIGINS` 환경변수 (기본값 없음, 7장) | 운영 프론트엔드 주소만 명시적으로 허용 |
| 알림 발송 채널 (#69) | `NOTIFICATION_SENDER`(`log` 기본 / `slack`), `SLACK_WEBHOOK_URL`(기본값 없음). `slack`인데 URL이 비어 있으면 `ProdProfileGuard`가 기동 실패. 시간 초과는 `NOTIFICATION_SLACK_CONNECT_TIMEOUT`(2s)·`NOTIFICATION_SLACK_READ_TIMEOUT`(3s) | URL이 곧 발송 권한이라 설정 파일·저장소·로그에 두지 않음(오류 메시지·`toString`에서도 가림). `log`는 URL이 필요 없어 조건부로만 검사. Slack은 운영 채널 1곳으로 보내는 데모 기능 — 동작·한계는 `architecture.md` Kafka 장 "Slack 발송" |
| 그 밖의 비밀 값 | Redis 비밀번호·TLS 등은 Spring Boot 표준 환경변수(`SPRING_DATA_REDIS_PASSWORD` 등) | 설정 파일·저장소에 비밀 값을 두지 않음. `.env` 파일도 읽지 않음(local 전용) |
| Swagger | `springdoc.api-docs.enabled`·`swagger-ui.enabled: false` 명시 + 켜져 있으면 `ProdProfileGuard`가 기동 실패 | 환경변수로 잘못 켜도 API 구조가 공개되지 않도록 |
| Actuator 포트 | 관리 포트 `MANAGEMENT_PORT`(기본 8081) — 서비스 포트(8080)에는 `/actuator`가 없음 | 관리 포트는 외부에 공개하지 않고 내부 헬스체크·Prometheus만 접근 (dev와 같은 구조) |
| Actuator 노출 | `endpoints.access.default: none` + health·prometheus만 `read-only`로 켜고 노출 | 노출 목록과 별개로 엔드포인트 자체를 끔 → env·heapdump·loggers 등은 ADMIN 토큰이 있어도 404 |
| 헬스체크 | `show-details: never`, `probes.enabled: true` (`/actuator/health/liveness`·`readiness`) | DB 등 내부 구성을 숨기고, 오케스트레이터용 프로브 제공. Redis 헬스체크는 기본 설정대로 끔(Redis 장애로 인스턴스가 빠지지 않도록) |
| 오류 응답 | `server.error.include-message/stacktrace: never`, `include-exception: false` | `GlobalExceptionHandler`가 처리하지 않는 오류(`/error`)에서 내부 정보 노출 방지 |
| 종료 | `server.shutdown: graceful` (단계별 최대 30초, 기본값) | 배포·재시작 때 처리 중인 결제 요청을 끝낸 뒤 종료 |
| SQL 로그 | `spring.jpa.show-sql: false` 명시 | 조회 조건이 로그에 남지 않고 로그 양으로 성능이 떨어지지 않도록 |
| Flyway baseline | `spring.flyway.baseline-on-migrate: false` + 켜져 있으면 `ProdProfileGuard`가 기동 실패 | 기본 설정(true)은 이력 테이블 없이 테이블만 있는 DB를 V1 적용 완료로 기록하고 진행 → `DB_URL`을 잘못 지정해도 조용히 마이그레이션됨. 끄면 그런 DB에서는 기동 실패. 빈 DB(첫 배포)·이력이 있는 DB는 영향 없음 |
| 커넥션 풀·DB 타임아웃 | 모든 프로필 공통 (`application.yml`) — 6장 | 잠금·실행·커넥션 대기에 상한 |

실행 예

```bash
docker build -t delivery .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://db.internal:5432/delivery -e DB_USERNAME=... -e DB_PASSWORD=... \
  -e JWT_SECRET=... -e REDIS_HOST=redis.internal -e KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092 \
  -e CORS_ALLOWED_ORIGINS=https://app.example.com \
  delivery
# 관리 포트 8081은 -p로 열지 않음 → 같은 네트워크의 Prometheus·헬스체크만 접근
```

## 3. 노출 정책 비교

| 경로 | local | dev | 기본(프로필 없음) | prod |
|---|---|---|---|---|
| Swagger UI·`/v3/api-docs` | 켬 | 켬 | 404 | 404 (켜면 기동 실패) |
| `/actuator/health` | 8080 | 8081 | 8080 | 8081 (+ liveness·readiness) |
| `/actuator/prometheus` | 8080 | 8081 | 404 | 8081 |
| `/actuator/metrics` | 8080 (ADMIN) | 404 | 404 | 404 |
| env·beans·heapdump 등 | 비노출 | 비노출 | 비노출 | 엔드포인트 비활성 (ADMIN도 404, 토큰 없으면 401) |

- 관리 포트에도 `SecurityConfig`가 그대로 적용됨 (토큰 없는 `/actuator/env` → 401, `ProdProfileIntegrationTest`로 확인)

## 4. 검증

| 테스트 | 확인한 내용 |
|---|---|
| `ProdProfileIntegrationTest` (6건) | `test,prod` 프로필로 실제 기동(임의 서비스·관리 포트) 후 HTTP 요청: 서비스 포트에 Actuator 없음(404)·API 정상, Swagger UI·명세 404, 관리 포트 health는 `UP`만(상세 없음)·liveness·readiness 200, prometheus 200, env·info·metrics·heapdump·loggers·beans·configprops는 ADMIN 토큰이어도 404·토큰 없으면 401, CORS는 `CORS_ALLOWED_ORIGINS` 출처만 허용(local 기본값 403) |
| `ProdProfileGuardStartupTest` (2건, #61) | `DB_URL`·`KAFKA_BOOTSTRAP_SERVERS`가 비어 있으면 DB 없이 `SpringApplication.run`해도 가드 메시지로 실패(DataSource보다 먼저), prod가 아닌 프로필은 검사하지 않음 |
| `ProdProfileGuardTest` (5건) | 필수 환경변수가 모두 있으면 통과, 없거나 공백이면 이름만 담아 실패(값은 메시지에 없음), `CORS_ALLOWED_ORIGINS`가 비어 있으면 실패, Swagger가 켜져 있으면 실패, Flyway 자동 baseline이 켜져 있으면 실패 |
| `CorsSecurityTest` (7건)·`CorsPropertiesTest` (4건) | 7장 |

- 수동 확인(로컬에서 직접 실행한 결과)은 [로컬 확인 가이드](local-verification.md) 3-2·3-3·4장 "검증 결과"에 따로 기록
| `DatabaseTimeoutIntegrationTest` (4건) | 풀의 커넥션에 `lock_timeout=5s`·`statement_timeout=30s` 적용, 풀 크기 10·커넥션 대기 10초, 다른 트랜잭션이 잡은 행 잠금 대기 초과 → `PessimisticLockingFailureException`, 문장 실행 초과 → `QueryTimeoutException` |
| `ErrorResponseFormatTest` (503 1건) | 위 예외와 커넥션 획득 실패가 503 공통 형식으로 응답, DB 오류 메시지는 응답에 없음 |

## 5. 한계 · 후속

- `SPRING_PROFILES_ACTIVE`를 빠뜨리면 `local`(기본 프로필)로 떠서 Swagger가 켜짐 → 배포 설정에 반드시 지정. 기본 프로필을 바꾸면 개발 PC 실행 방법이 달라져 이번에는 유지
- 프록시 뒤 배포 시 `server.forward-headers-strategy`, TLS 종료 위치, JVM 옵션은 배포 환경이 정해진 뒤 결정
- 커넥션 풀 크기·타임아웃 값은 기본값 수준의 출발점 — 실제 트래픽·인스턴스 수·DB `max_connections`로 조정 (6장)
- Flyway 도입 전부터 쓰던 DB를 prod로 올릴 일이 생기면 자동 baseline 대신 `flyway baseline -baselineVersion=1`을 한 번 직접 실행
- Alertmanager 연동·Redis 고가용성은 기존 후속 과제 그대로

## 6. 커넥션 풀 · DB 타임아웃

`application.yml`의 `spring.datasource.hikari` — 프로필과 관계없이 적용 (테스트도 같은 값으로 실행).

| 항목 | 값 | 환경변수 | 이유 |
|---|---|---|---|
| 풀 크기 `maximum-pool-size` | 10 (Hikari 기본값 명시) | `DB_POOL_SIZE` | 인스턴스 수 × 풀 크기가 PostgreSQL `max_connections`(기본 100)를 넘지 않아야 함 |
| 커넥션 대기 `connection-timeout` | 10초 (기본 30초) | - | 풀이 바닥났을 때 요청이 30초씩 매달리지 않도록. `lock_timeout`보다 길게 둬 잠금 대기 중인 요청이 먼저 정리됨 |
| `lock_timeout` | 5초 (PostgreSQL 기본: 무제한) | `DB_LOCK_TIMEOUT` | 주문·결제·매장·정산 행을 `FOR UPDATE`로 잠그는데, 상한이 없으면 긴 트랜잭션 하나가 같은 행을 건드리는 요청을 모두 붙잡고 풀까지 고갈시킴 |
| `statement_timeout` | 30초 (PostgreSQL 기본: 무제한) | `DB_STATEMENT_TIMEOUT` | 문장 1개의 실행 시간 상한(잠금 대기 포함). 트랜잭션 전체 시간은 제한하지 않음 |

- 전달 방식: pgjdbc `options`(`-c lock_timeout=... -c statement_timeout=...`) → 접속할 때 세션 값으로 지정. DB·계정 설정(`ALTER ROLE`)을 바꾸지 않아도 되고 설정 파일에서 값이 보임
- 넘으면: 그 문장만 실패하고 트랜잭션은 롤백 → `GlobalExceptionHandler`가 **503**으로 응답 (잠금 대기 초과·교착 상태, 문장 실행 초과, 커넥션 획득 실패). 데이터는 반영되지 않았으므로 클라이언트는 다시 시도하면 됨
- 스케줄러·Outbox 발행처럼 요청 밖에서 실행되는 작업은 기존 실패 처리 그대로 (정산 집계는 실패 기록 후 다음 실행에서 재시도, Outbox는 다음 주기에 다시 조회)

부작용 · 주의

- **Flyway도 같은 풀을 사용** → 마이그레이션 문장에도 두 타임아웃이 적용됨. 큰 테이블의 인덱스 생성·백필처럼 30초를 넘길 수 있는 마이그레이션은 그 파일 안에서 `SET LOCAL statement_timeout = 0;`으로 풀어야 함 ([DB 마이그레이션](database.md)). `lock_timeout`은 그대로 두는 편이 안전 (DDL이 잠금을 기다리는 동안 뒤따르는 조회까지 막는 일을 방지)
- 정상 상황에서 5초 넘게 잠금을 쥐는 트랜잭션은 없음: 가장 긴 것은 매장 1곳의 정산 집계(매장 행 잠금)이고 이때 같은 매장의 수수료율 변경·재집계가 기다림. 매장 하루치 결제가 매우 많아져 집계가 5초를 넘기면 기다리던 쪽이 503 → 그때는 값 조정
- `idle_in_transaction_session_timeout`은 적용하지 않음: Outbox 발행이 Kafka 응답을 기다리는 동안(최대 약 15초) 트랜잭션을 열어 두므로, 값을 잘못 잡으면 발행 중인 커넥션이 끊김
- 풀 크기 10은 인스턴스 1대 기준 출발점. 동시 요청이 풀보다 많으면 커넥션을 기다리다 10초 뒤 503

## 7. CORS (#61)

브라우저 프론트엔드가 다른 출처(도메인·포트)에서 API를 호출하기 위한 설정. `SecurityConfig.corsConfigurationSource()` + `config.CorsProperties`

| 항목 | 설정 | 이유 |
|---|---|---|
| 허용 출처 | `cors.allowed-origins` ← `CORS_ALLOWED_ORIGINS`(쉼표 구분). 기본(application.yml) 빈 목록, local·dev 기본 `http://localhost:5173,http://localhost:3000`, prod는 기본값 없이 필수 | 출처를 코드가 아닌 배포 설정으로 관리. prod에서 빠뜨리면 `ProdProfileGuard`가 기동을 막음 |
| 출처 형식 검사 | `scheme://host[:port]`만 허용, 와일드카드(`*`)·경로·끝 슬래시는 바인딩 단계에서 기동 실패 | 와일드카드는 어느 사이트든 사용자의 브라우저로 API를 호출하게 함. 끝 슬래시는 브라우저 `Origin` 헤더와 절대 일치하지 않아 조용히 모두 막히므로 기동 시점에 드러나게 함 |
| 자격 증명 | `allowCredentials: true` (V15, #61에서는 `false`) | Refresh Token을 HttpOnly 쿠키로 주고받기 위해. API 인증은 여전히 `Authorization` 헤더(JWT)이고 쿠키는 `refresh_token`(`Path=/api/users`) 하나뿐. 허용 출처 목록이 곧 재발급·로그아웃 CSRF 방어의 한 축이 됨 ([프로젝트 구조 · 인증](architecture.md#인증-access--refresh-token-v15)) |
| 허용 메서드 | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | 컨트롤러에서 쓰는 메서드 전체 (권한은 기존 URL 규칙이 그대로 판단) |
| 허용 요청 헤더 | `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Trace-Id` | JWT, JSON 본문, 결제 멱등성 키(#41), 클라이언트가 정하는 추적 id(#38). 목록에 없는 헤더는 Preflight 403 |
| 노출 응답 헤더 | `X-Has-Next`, `X-Next-After-Id`, `X-Page`, `X-Size`, `X-Trace-Id`, `Idempotent-Replayed` | 브라우저 JS는 기본 헤더 외에는 노출 목록에 있는 것만 읽을 수 있음 (목록 페이징·오류 추적·멱등 재전송 여부) |
| Preflight 캐시 | `maxAge` 1시간 | 요청마다 OPTIONS가 한 번 더 가지 않도록 |
| Preflight 인증 | Spring Security `CorsFilter`가 인증·권한 검사 앞에서 처리 → 허용 출처의 Preflight는 토큰 없이 200 | 브라우저는 Preflight에 토큰을 붙이지 않음. `OPTIONS /**`를 permitAll로 열지 않는 이유: CORS가 아닌 OPTIONS까지 인증 없이 컨트롤러 쪽으로 넘기지 않기 위해 |

동작
- 허용하지 않은 출처: Preflight·실제 요청 모두 `CorsFilter`가 403(`Invalid CORS request`)으로 끝냄 → 토큰이 있어도 컨트롤러에 닿지 않음
- `Origin` 헤더가 없는 요청(curl·서버 간 호출·헬스체크)과 같은 출처 요청은 CORS 대상이 아니라 기존 동작 그대로 — CORS는 브라우저 보호 장치이지 API 접근 제어가 아님 (접근 제어는 JWT·URL 규칙)
- 관리 포트(8081)에도 같은 필터 체인이 적용되지만 브라우저에서 호출할 일이 없음

Refresh Token 쿠키 (V15) — `auth.refresh-token.*` (`config.RefreshTokenProperties`)

| 환경변수 | 기본 | 설명 |
|---|---|---|
| `REFRESH_TOKEN_COOKIE_SECURE` | `true` | HTTPS에서만 전송. Chrome·Firefox는 `http://localhost`도 보안 출처로 취급해 로컬 개발에서도 그대로 동작 (Safari 등에서 로컬 쿠키가 저장되지 않으면 local에서만 `false`) |
| `REFRESH_TOKEN_COOKIE_SAME_SITE` | `Strict` | 프론트·API가 같은 사이트(등록 도메인 같음, 포트 무관 — `localhost:5173` ↔ `localhost:8090`, `app.example.com` ↔ `api.example.com`)면 `Strict`. 다른 사이트면 `None`(+ `Secure` 필수, 아니면 기동 실패) — 이때 CSRF 방어는 CORS 허용 출처 검사에만 의존 |
- 수명(`ttl` 14일, 로그인 후 절대 만료 `absolute-ttl` 30일 — V16)과 Access 수명(`jwt.expiration-ms` 15분)은 application.yml 값. 배포(#86)는 Vercel rewrite로 API를 같은 출처로 프록시하므로 기본값 `Strict` 그대로 (8장)

변경 방법
- 출처 추가: 배포 환경의 `CORS_ALLOWED_ORIGINS`에 쉼표로 추가 후 재시작 (예: `https://app.example.com,https://admin.example.com`)
- compose(dev) 앱은 이 변수를 넘기지 않으므로 dev 기본값 사용. 다른 주소가 필요하면 compose `app.environment`에 추가

한계 · 후속
- 거부 응답 본문은 Spring 기본 문자열이라 공통 오류 형식(`ApiErrorResponse`)이 아님 — 브라우저 JS는 읽을 수 없어 영향 없음
- 실제 브라우저 확인은 프론트엔드 연동 때 (이번 수동 확인은 curl로 헤더를 흉내 냄 — [로컬 확인 가이드](local-verification.md) 4장)

## 8. 배포 — Oracle Cloud Always Free VM + Vercel (#86)

포트폴리오 공개용 무료 배포. 백엔드는 VM 1대에서 compose로, 프론트는 Vercel 정적 호스팅.
고른 이유·다른 안(Render·Koyeb + Neon + Upstash)과 비교는 [로드맵](roadmap.md) 배포 장.

```
브라우저 ── https://xxx.vercel.app ──► Vercel (정적 파일)
              │  /api/* · /oauth2/* · /login/oauth2/*  (vercel.json rewrite = 프록시)
              ▼
         https://API_DOMAIN ──► Caddy(80·443, 자동 HTTPS) ──► app:8080 ──► db · redis · kafka (compose 내부망)
              ▲
              └ (선택) 사장님 SSE만 직접 연결: VITE_STREAM_BASE_URL
```

파일: `deploy/compose.prod.yml`(prod 프로필 앱 + db·redis·kafka + Caddy, 선택 monitoring), `deploy/Caddyfile`, `deploy/.env.prod.example`, `frontend/vercel.json`, `frontend/.env.production`

### 8-1. 설계 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| API 호출 경로 | 프론트는 같은 출처 `/api`(`VITE_API_BASE_URL` 빈 값) → Vercel rewrite가 API 도메인으로 프록시 | `*.vercel.app`과 API 도메인이 다르면 Refresh 쿠키가 다른 사이트(third-party) 쿠키가 되어 Safari 등에서 차단 → 새로고침마다 로그아웃. 같은 출처면 기존 `SameSite=Strict` + `Secure` 그대로 동작 (SameSite=None 불필요) |
| 소셜 로그인 경로 | `/oauth2/*`·`/login/oauth2/*`도 Vercel 경유, 콜백 주소는 `SOCIAL_REDIRECT_BASE_URL`=프론트 주소로 고정 | 시작 기록 쿠키와 성공 후 Refresh 쿠키가 프론트 도메인에 심겨야 함. 프록시를 두 번 거치면 앱이 보는 Host가 API 도메인이라 `{baseUrl}`로는 콜백 주소가 틀어짐 → forward 헤더 신뢰 설정 대신 설정값으로 고정 (`SocialLoginProperties.redirectBaseUrl`) |
| SSE | 기본은 Vercel 경유, 끊기거나 늦으면 `VITE_STREAM_BASE_URL=https://API_DOMAIN` | 외부 rewrite의 스트리밍·연결 시간 제한은 실측 전. SSE는 Bearer만 쓰므로 직접 연결해도 CORS 허용(`CORS_ALLOWED_ORIGINS`=프론트 주소)만 있으면 됨. 끊겨도 프론트가 재연결·폴링으로 대체(#76) |
| HTTPS | Caddy 자동 인증서(Let's Encrypt). 도메인이 없으면 `<공인IP 대시>.sslip.io` | Vercel rewrite 대상과 Secure 쿠키에 HTTPS 필요. 인증서는 `caddy-data` 볼륨에 보관(재발급 횟수 제한) |
| 공개 포트 | 80·443(Caddy)만. 관리 포트 8081·DB·Redis·Kafka·Grafana는 외부 비공개 | prod 프로필 노출 정책(3장)과 같음. Grafana는 SSH 터널 |
| 결제 | 토스 `test_` 키 또는 키 없이 모의 결제. 가짜 PG(`TOSS_MODE=fake`)는 쓰지 않음 | `ProdProfileGuard`가 prod에서 가짜 PG를 막음(승인 없는 결제 기록 방지) — 데모를 위해 가드를 풀지 않음 |
| 메모리 | 앱 힙 `-Xmx1g`, Kafka 힙 `-Xmx512m` | 무료 VM(ARM) 한 대에 전부 올리므로 상한을 고정 |

### 8-2. VM 준비 (사용자)

1. Oracle Cloud 가입(카드 인증) → Compute 인스턴스 생성: Ubuntu 24.04, Shape `VM.Standard.A1.Flex`(Ampere ARM, Always Free 한도 안), 공인 IP, SSH 키
   - 리전 용량 부족(`Out of capacity`)이면 다른 가용 도메인·시간대에 재시도
   - Always Free 한도는 바뀔 수 있으니 생성 화면의 "Always Free-eligible" 표시로 확인
2. VCN 보안 목록(Security List) Ingress에 TCP 80·443 추가 (소스 `0.0.0.0/0`)
3. **Oracle Ubuntu 이미지는 OS 방화벽(iptables)이 22번만 열어 둠** → 보안 목록만 열면 접속이 안 됨
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
4. Docker 설치: `curl -fsSL https://get.docker.com | sh` → `sudo usermod -aG docker $USER` 후 재접속
5. 유휴 회수 주의: Always Free 인스턴스는 일정 기간 CPU·네트워크 사용이 매우 낮으면 회수될 수 있음 (조건은 Oracle 문서 확인)

### 8-3. 백엔드 실행 (VM)

```bash
git clone <저장소 주소> && cd Spring-Delivery-mini
cp deploy/.env.prod.example deploy/.env.prod   # 값 입력 (API_DOMAIN, FRONTEND_ORIGIN, DB_PASSWORD, JWT_SECRET 등)
docker compose -f deploy/compose.prod.yml --env-file deploy/.env.prod up -d --build
docker compose -f deploy/compose.prod.yml --env-file deploy/.env.prod logs -f app caddy
```
- 첫 빌드는 ARM VM에서 Gradle이 돌아 수 분 걸림. 빈 DB라 Flyway가 V1~최신을 적용(prod는 `baseline-on-migrate: false` — 빈 DB는 영향 없음)
- 필수 값이 비면 compose(`:?`)나 `ProdProfileGuard`가 빠진 항목을 출력하고 멈춤
- 확인: `curl -i https://$API_DOMAIN/api/stores` → 200, 인증서 발급 로그는 `logs caddy`
- 갱신 배포: `git pull` 후 같은 `up -d --build` (앱만 재생성, graceful shutdown)
- 모니터링: `--profile monitoring` 추가 + `GRAFANA_ADMIN_PASSWORD`, 접속은 `ssh -L 3000:localhost:3000 ubuntu@<VM IP>` → http://localhost:3000
- DB 백업(선택): `docker compose -f deploy/compose.prod.yml exec -T db pg_dump -U $DB_USERNAME delivery > backup.sql`

### 8-4. 프론트 배포 (Vercel, 사용자)

1. `frontend/vercel.json`의 `API_DOMAIN`(3곳)을 실제 API 도메인으로 바꿔 커밋 — rewrite 대상은 정적 설정이라 환경변수를 못 씀
2. Vercel에서 저장소 가져오기 → Root Directory `frontend`, Framework `Vite`(빌드 `npm run build`, 출력 `dist`)
3. 환경변수(선택): `VITE_STREAM_BASE_URL`(SSE 직접 연결 시). 체험 계정 버튼은 환경변수가 아니라 서버(`GET /api/users/demo-accounts`, 9-6)에서 받음
   - `VITE_API_BASE_URL`은 `frontend/.env.production`이 빈 값(같은 출처)으로 정함
4. 배포 주소(`https://xxx.vercel.app`)를 VM의 `FRONTEND_ORIGIN`에 넣고 앱 재생성
5. 직접 접속·새로고침(`/owner/orders` 등)은 마지막 rewrite가 `index.html`로 보냄 (정적 파일이 먼저 매칭됨)

### 8-5. 소셜 로그인 콘솔 (사용자)

- Redirect URI: `{FRONTEND_ORIGIN}/login/oauth2/code/kakao`, `{FRONTEND_ORIGIN}/login/oauth2/code/google` (API 도메인이 아님 — 8-1)
- 카카오: 플랫폼 Web 사이트 도메인에 Vercel 주소 추가
- 구글: OAuth 동의 화면이 "테스트" 상태면 등록한 테스트 사용자만 로그인 가능 → 공개 데모는 "프로덕션"으로 게시(이메일·프로필 범위)

### 8-6. 초기 데이터

prod DB는 빈 상태. 데모용 계정은 화면(API)으로 만든다
1. 관리자: 회원가입(CUSTOMER) 후 DB에서 승격 — `docker compose -f deploy/compose.prod.yml exec db psql -U $DB_USERNAME delivery -c "UPDATE users SET role='ADMIN' WHERE username='<아이디>'"` (가입 API는 ADMIN을 받지 않음)
2. 사장님: 가입 → 사장님 전환 신청 → 관리자 승인 → 매장·메뉴 등록
3. 고객: 가입 → 주문 → 결제(모의 결제 또는 토스 테스트 키)

### 8-7. 확인 항목 (배포 후)

| 항목 | 방법 |
|---|---|
| HTTPS·프록시 | `https://xxx.vercel.app/api/stores` 200 |
| Refresh 쿠키 | 로그인 후 새로고침해도 로그인 유지 (Safari 포함), 응답 `Set-Cookie`가 vercel.app 도메인 |
| SPA 새로고침 | `/owner/orders`에서 새로고침 → 404 아님 |
| SSE | 사장님 화면 접속 → 다른 브라우저에서 결제 → 토스트. 늦거나 끊기면 `VITE_STREAM_BASE_URL` |
| 소셜 로그인 | 카카오·구글 로그인 → `/oauth/callback` → 로그인 유지 |
| 비공개 포트 | 외부에서 `:8081`·`:5432`·`:3000` 접속 불가 |

한계
- 단일 VM: VM·디스크 장애 시 서비스 중단·데이터 손실(백업은 수동). 학습·시연용
- Vercel 외부 rewrite의 SSE 동작, Oracle 유휴 회수 조건은 배포 후 실측·확인 필요

## 9. 무료 시연 배포 — Render Free + Neon + Upstash + Vercel (`prod-free`, 2026-11-23)

8장(Oracle A1)은 용량 부족으로 VM을 받지 못해, **월 $0·카드 등록 없이** 시연할 수 있는 구성을 따로 둠 (8장 구성은 A1을 받으면 그대로 사용 가능).
무료 플랜 조사(2026-11-23, 공식 문서 기준): 백엔드는 Render 무료 웹 서비스(512MB·0.1 CPU, 15분 유휴 시 절전, 영구 디스크 없음), DB는 Neon Free(PG 18, 5분 유휴 시 정지, 월 100 CU-시간), Redis는 Upstash Free(월 50만 명령, 250MB).
영구 무료 Kafka는 Aiven Free뿐인데 유휴 시 꺼지고 콘솔에서 직접 켜야 해 시연에 부적합 → **Kafka는 빼고 이벤트를 같은 프로세스에서 직접 전달**.

```
브라우저 ── https://xxx.vercel.app ──► Vercel (정적 파일)
              │  /api/* · /oauth2/* · /login/oauth2/*  (vercel.json rewrite = 프록시)
              ▼
     Render Free 웹 서비스 (Docker, SPRING_PROFILES_ACTIVE=prod,prod-free, PORT)
              ├─► Neon Free (PostgreSQL 18, TLS)
              └─► Upstash Redis Free (TLS)
              (Kafka 없음: outbox_events → DirectOutboxSender → 알림 저장·SSE)
```

### 9-1. 설계 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 프로필 | `prod,prod-free` (prod 위에 덮어씀) | prod의 점검(`ProdProfileGuard`)·오류 숨김·API 문서 비활성을 그대로 유지하고 다른 점만 `application-prod-free.yml`에 둠 |
| Kafka | `outbox.delivery=direct`, 리스너 자동 시작·토픽 생성 끔 | Outbox 저장·행 잠금·백오프·FAILED·관리자 재발행·중복 제거(`consumed_events`)는 그대로. Consumer 재시도·DLT 대신 발행 재시도(최대 10번) 후 FAILED (`docs/architecture.md` Kafka 장 "direct 전달") |
| Kafka 주소 검사 | direct 모드면 `KAFKA_BOOTSTRAP_SERVERS` 필수에서 제외 | 나머지 필수 값·CORS·가짜 PG 금지 검사는 그대로 |
| 포트 | `server.port=${PORT:8080}`, Actuator도 같은 포트(health만) | 공개 포트가 하나뿐. prometheus는 노출하지 않음, health 상세 숨김 유지 |
| 커넥션 풀 | 최대 5 | 무료 DB 연결 한도. direct 전달이 발행 트랜잭션 안에서 트랜잭션을 하나 더 열어(REQUIRES_NEW) 2 이상 필요 |
| JVM 옵션 | 이미지에 넣지 않고 `JAVA_TOOL_OPTIONS`로 | compose(dev)에 영향 없도록. 값은 9-3 |
| 서버 절전 | 프론트가 확인 요청으로 깨우고 안내 배너 표시 | `frontend/README.md` "서버 깨우기" |
| 24시간 깨워 두기 | **하지 않음** | Outbox 발행기가 1초마다 DB를 조회 → 앱이 깨어 있는 동안 Neon도 계속 켜짐. 0.25 CU로 한 달이면 약 182 CU-시간으로 무료 100을 넘음 (Neon 무료 최소 CU는 미확인) |
| 인증·BCrypt | 변경 없음 | 0.1 CPU에서 로그인 약 1~3.5초(측정)는 감수 |

### 9-2. 환경변수 (Render)

| 변수 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod,prod-free` |
| `DB_URL` | `jdbc:postgresql://<Neon direct 호스트>/<db>?sslmode=require` — **pooler가 아닌 direct 엔드포인트** (Flyway·`FOR UPDATE SKIP LOCKED`·락이 트랜잭션 풀링과 충돌하지 않도록) |
| `DB_USERNAME`·`DB_PASSWORD` | Neon 계정 |
| `JWT_SECRET` | 32바이트 이상 임의 값 |
| `REDIS_HOST`·`REDIS_PORT` | Upstash 엔드포인트·포트 |
| `SPRING_DATA_REDIS_SSL_ENABLED`·`SPRING_DATA_REDIS_PASSWORD` | `true`·Upstash 토큰 (사용자 이름이 필요하면 `SPRING_DATA_REDIS_USERNAME`) |
| `CORS_ALLOWED_ORIGINS` | Vercel 주소 |
| `JAVA_TOOL_OPTIONS` | `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k -XX:MaxRAMPercentage=60` |
| `PORT` | Render가 지정 (지정하지 않으면 8080) |
| 소셜 로그인 | `KAKAO_CLIENT_ID`·`KAKAO_CLIENT_SECRET`·`GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET` — **없으면 `/api/users/social/providers`가 `[]`라 버튼이 안 보임** |
| 소셜 주소 | `SOCIAL_REDIRECT_BASE_URL`=`https://{프론트}`, `SOCIAL_FRONTEND_CALLBACK_URL`=`https://{프론트}/oauth/callback` (키가 있으면 뒤쪽은 필수 — prod 점검) |
| 토스 키 | 8장과 같음. `TOSS_MODE=fake`는 prod 점검이 막음 |
| 비밀번호 찾기 (V23)·이메일 인증 (V24) | `MAIL_SENDER=brevo`, `BREVO_API_KEY`, `MAIL_FROM_ADDRESS`(Brevo에서 인증한 주소), `PASSWORD_RESET_URL`=`https://{프론트}/password-reset`, **`EMAIL_VERIFICATION_URL`=`https://{프론트}/email-verification`(V24 추가)** — 비우면 `none`(비밀번호 찾기·이메일 등록 503), brevo면 넷 다 필수 (prod 점검) |
| 데모 데이터 | `DEMO_DATA_ENABLED=true`, `DEMO_PASSWORD`(공개), `DEMO_ADMIN_PASSWORD`(비공개, 선택) — 9-6 |

`{프론트}`는 **Vercel 프로덕션 도메인**(`<프로젝트>.vercel.app`). 브랜치 미리보기 주소(`<프로젝트>-git-<브랜치>-<팀>.vercel.app`)는
Vercel Authentication(Deployment Protection)으로 막혀 있어 외부 방문자·카카오·구글 콜백이 들어오지 못함(302 → Vercel 로그인).
CORS·소셜·재설정 주소를 모두 같은 프로덕션 도메인으로 맞춤.

Render 헬스체크 경로를 지정할 수 있으면 `/actuator/health/liveness` (외부 의존성과 무관 → DB 일시 장애로 재시작이 반복되지 않음).
프론트(Vercel)는 8-4와 같고 `vercel.json`의 rewrite 대상 3곳을 Render 주소(`https://<서비스>.onrender.com`)로 바꿔 **main에 커밋**
(Vercel은 저장소의 파일로 빌드 — 로컬 수정만으로는 반영되지 않음. 자리표시자 그대로면 `/api/*`가 백엔드에 닿지 않아 로그인·목록 모두 실패).

### 9-3. 로컬 측정 (512MB · `--cpus` 상한, 2026-11-23)

Docker로 Render 무료 사양을 흉내 낸 값 — **Render 실제 CPU 배분은 미확인**이라 배포 후 다시 잼.

| 구성 | CPU | 기동 | 메모리(기동 직후 → 요청 후) | 첫 로그인 | 목록 평균 |
|---|---|---|---|---|---|
| dev, JVM 기본값, Kafka 연결 | 0.1 | 430초 | 375 → 396MB | 24.0초 | 0.364초 |
| dev, JVM 조정 + 전역 지연 초기화 | 0.1 | 253초 | 311 → 331MB | 17.9초 | 0.134초 |
| dev, 위 + AppCDS | 0.1 | 116초 | 270 → 289MB | 11.0초 | 0.207초 |
| **prod,prod-free, JVM 조정(지연 초기화 없음), DB 준비된 재기동** | 0.1 | **300초** | 303 → 319MB | 3.6초 | 0.149초 |
| 〃 새 DB(마이그레이션 22개 포함) | 0.1 | 330초 | 303 → 317MB | 2.7초 | 0.081초 |
| 〃 | 1.0 | 25초 | 309 → 323MB | 0.18초 | 0.019초 |

- 모든 경우 OOM 없음, prod-free에서 Kafka 접속 시도 로그 0줄, ERROR 로그 0건, `/actuator/health` 200 · `/actuator/prometheus` 404
- **전역 지연 초기화(`spring.main.lazy-initialization`)는 쓰지 않음**: `@Scheduled`(정산)·`@KafkaListener` 빈이 만들어지지 않아 등록되지 않을 수 있음. 기동은 약 1분 느려짐
- 0.1 CPU가 엄격히 적용되면 절전 후 첫 접속에 약 5분 → 시연 전 미리 접속해 깨워 둘 것

### 9-4. AppCDS 검토 (보류)

- 효과: 같은 조건에서 기동 253초 → 116초 (dev, 지연 초기화 포함 측정)
- 문제: 학습 실행(`-XX:ArchiveClassesAtExit` + `-Dspring.context.exit=onRefresh`)이 컨텍스트를 끝까지 올려 **Flyway·Hibernate 검증이 DB에 접속** → Render의 Docker 빌드 단계(DB 없음)에서는 그대로 못 씀
- 방법 후보: ① 학습 때만 Flyway·JDBC 메타데이터 접근·Kafka 토픽 생성을 끄는 설정(검증 필요) ② GitHub Actions에서 DB를 띄워 학습한 이미지를 GHCR에 올리고 Render가 이미지로 배포(Render 무료 플랜의 이미지 배포 지원은 미확인)
- 아카이브는 JDK·클래스패스가 같아야 쓰임(같은 이미지 안에서 만들어야 함), 이미지가 약 150MB 커짐
- 결정(2026-11-23): **Render 실측 후** 기동이 너무 느릴 때만 ①부터 검증

### 9-5. 확인 항목 (배포 후, 현재 미확인)

| 항목 | 방법 |
|---|---|
| 실제 기동 시간 | Render 로그 `Started ... in N seconds`, 절전 후 첫 접속 시간 |
| 기동 대기 제한 | 기동이 수 분 걸릴 때 Render가 실패로 처리하는지 |
| `PORT`·헬스체크 | 포트 감지, 헬스체크 경로 지정 가능 여부 |
| 프록시 대기 한도 | 절전 중 Vercel rewrite가 504를 주는지 → 프론트 배너·GET 재시도로 대응 |
| Neon·Upstash 사용량 | 한 달 CU-시간·명령 수가 무료 한도 안인지 |
| 정산 스케줄러 | 01:10에 절전 중이면 실행되지 않음 → 시연은 관리자 재집계 API로 |
| 기능 | 8-7 표 (비공개 포트 항목 제외) + 결제 후 알림 생성·사장님 SSE 토스트 |
| 소셜 버튼 | `https://{프론트}/api/users/social/providers`가 `["kakao","google"]` |
| 비밀번호 찾기 | 가입 시 이메일 입력 → 인증 메일 링크 → '이메일 인증하기' → 로그인 화면 "비밀번호를 잊으셨나요?" → 메일 링크 → 새 비밀번호로 로그인, 다른 기기 로그아웃 |
| 데모 데이터 | Render 로그 `데모 기본 데이터 생성 완료`·`데모 정산 생성 완료`, 로그인 화면 체험하기 버튼 3개로 고객·사장님·관리자 로그인 |

### 9-6. 데모 데이터 (`demo-data.*`, `maintenance.DemoDataSeeder`)

배포 DB(Neon)는 새로 만든 빈 DB라 로컬에서 보던 테스트 데이터(로컬 PostgreSQL)가 없음. 로컬 데이터를 옮기지 않고 **기동 시 시드**로 만듦
(재현 가능, DB를 갈아엎어도 다시 생김, 로컬 데이터는 그대로).

| 만드는 것 | 내용 |
|---|---|
| 관리자 `demoadmin` | 방문자는 비밀번호 없이 **관리자 체험 로그인**(`POST /api/users/demo-login/admin`)으로 들어옴 — 데모 데이터만 다룸(아래). `DEMO_ADMIN_PASSWORD`는 운영자가 아이디·비밀번호로 확인할 때만(선택). 같은 아이디가 이미 있으면 그대로 씀 |
| 사장님 `demoowner` | 고객 가입 → 전환 신청 → `demoadmin` 승인 (V17 경로 그대로), 매장 2개(데모 치킨·데모 분식) + 메뉴 6개 |
| 고객 `democustomer` | 주문 4건: 배달완료·수락(배달 중)·결제완료(수락 대기)·주문요청(결제 대기) — 결제 3건은 모의 결제, 수수료는 `Payment` 생성자 계산. 시각은 주문(결제 2분 전) → 결제 → 배달완료(결제 40분 뒤) 순서, 지금보다 늦은 시각 없음 |
| 신청자 `demoapplicant` (2단계) | 승인 대기 사장님 전환 신청 1건(데모 떡집) — 관리자 승인 시연용, 비밀번호 없음(로그인 불가) |
| 지난 3일 정산 (2단계) | 매장마다 하루 배달완료 주문(치킨 2·분식 1) → 스케줄러와 같은 집계로 정산 6건 → 가장 오래된 날 **지급**, 다음 날 **확정**, 마지막 날 **대기** (관리자가 확정·지급을 바로 해 볼 수 있음) |

- 멱등, 두 단계: 1단계는 `demoowner`, 2단계는 `demoapplicant`가 있으면 건너뜀 → **이미 1단계만 있는 배포 DB도 재배포하면 2단계만 더해짐**. 단계마다 한 트랜잭션이라 중간 실패 시 그 단계 전부 롤백 → 다음 기동에 다시
- 2단계 정산 날짜는 **데모 매장이 생긴 날보다 앞**(최대 어제까지): 이미 집계된 날짜에 주문을 더하면 그 주문은 정산되지 않으므로. 집계가 실패한 매장은 집계 실패 기록에 남음 → 관리자 재집계(`storeId` 지정)
- 결제 완료 이벤트(Outbox)·알림은 만들지 않음 (지난 일을 기록하는 데이터). 정산은 다음 날 스케줄러 또는 관리자 재집계 API
- **데모 계정(고객·사장님·관리자·신청자)은 비밀번호·이메일 변경, 소셜 계정 연결, 사장님 전환 신청이 403 `DEMO_ACCOUNT_PROTECTED`** — 방문자 한 명이 바꾸면 데모가 깨지므로 (화면에서도 숨김)
- 주문 시각 보정(2026-11-24): 예전 시드는 주문 시각이 저장 시각(지금)으로 남아 "결제·배달보다 늦게 주문"으로 보였음. 시더는 기존 데이터가 있으면 건너뛰므로 **이미 배포된 DB는 재배포 후 "데모 데이터 초기화" 버튼을 한 번 눌러야** 고쳐진 시각으로 다시 만들어짐
- 로그인 화면 "시연 계정으로 체험하기": 고객·사장님 체험하기는 아이디·공개 비밀번호를 채움(로그인은 직접), 관리자 체험하기는 바로 로그인. 목록은 서버 `GET /api/users/demo-accounts` → **Vercel 환경변수 불필요**, 관리자 비밀번호는 빌드 결과·응답 어디에도 없음
- **데모 관리자 범위**: 데모 매장 정산·결제·수수료율, 데모 회원의 전환 신청만. Outbox는 403, 다른 방문자 데이터는 목록에서 빠지고 처리 시 403 `DEMO_ADMIN_SCOPE` (`docs/api.md` 배포 데모 체험)
  - 방문자(소셜 가입 등)가 낸 사장님 전환 신청은 체험 관리자 화면의 승인 대기·승인됨·거절됨 어디에도 안 보임(의도, 화면에 안내 문구 — 2026-11-24 점검에서 "신청 누락"으로 보였던 원인). 저장 여부는 Neon SQL Editor에서 확인:
    `SELECT a.id, a.status, a.business_name, a.requested_at FROM owner_applications a JOIN users u ON u.id = a.user_id WHERE u.username = '<아이디>';`
- **초기화**: 관리자 정산 관리 화면 "데모 데이터 초기화" 버튼(`POST /api/admin/demo-data/reset`, 데모 관리자도 가능) → 방문자가 확정·지급·승인·주문한 결과를 지우고 처음 상태로. 데모 회원은 남아 로그인 유지, 다른 방문자 매장은 그대로, 1분 간격 제한 (`docs/api.md` 배포 데모 체험)
- 초기화 뒤 방문자 브라우저의 장바구니(localStorage)는 서버가 지울 수 없어 사라진 메뉴 id를 가리킬 수 있음 → 장바구니 화면이 담긴 메뉴를 `GET /api/menus/{id}`로 다시 확인해 "판매 종료" 안내·빼기 버튼, 그동안 주문 버튼 막음(#98, `frontend/src/cart/cartValidation`). 확인 없이 주문해도 서버가 404 `MENU_NOT_FOUND`로 막음
- 주문 목록은 주문 시각 최신순(#98) — 2단계 지난 3일 주문은 나중에 만들어 id가 크지만 시각이 과거라 아래에 나옴. 승인 대기 신청 "데모 떡집"의 신청 시각은 시드 시각 2시간 전(`now.minusHours(2)`, 대기 중인 신청처럼 보이도록) — 실제 확인 시각과 2시간 차이가 나는 것은 시간대 버그가 아님
- 초기화는 결제·정산 기록 불변 원칙의 예외 — 실제 거래가 아닌 데모 매장 데이터만, `demo-data.enabled=true`일 때만(꺼진 배포는 404)
- 회원까지 다시 만들려면: 데모 행을 지우고 재시작 (또는 Neon 브랜치 초기화)
- 비밀번호는 정해진 값이 없음 — 배포하는 사람이 정해 Render에 넣음(8자 이상). 기록은 `.env`(git 제외), 변수 설명은 `.env.example` 끝 "배포 데모 데이터"
- 배포 화면에 데모 데이터가 없으면 먼저 Render에 `DEMO_DATA_ENABLED=true`가 있는지 확인 (기본 `false`라 없으면 시드 빈 자체가 생기지 않음, 로그에 데모 관련 줄 0개)

### 9-7. 비밀번호 찾기·이메일 인증 메일 (Brevo)

Render Free는 SMTP 포트(25·465·587)로 나가는 연결을 막아 Gmail SMTP 등을 쓸 수 없음 → HTTPS API로 보내는 Brevo 사용
(무료 하루 300통, 도메인 없이 발신 주소 1개 인증으로 발송 가능).

1. app.brevo.com 가입 → Senders, Domains & Dedicated IPs → Senders에서 발신 주소 추가·인증 메일 확인
2. SMTP & API → API Keys → 키 생성 → Render `BREVO_API_KEY`
3. Render `MAIL_SENDER=brevo`, `MAIL_FROM_ADDRESS`(1의 주소), `PASSWORD_RESET_URL=https://{프론트}/password-reset`, `EMAIL_VERIFICATION_URL=https://{프론트}/email-verification`(V24)
4. 확인: 가입(이메일 입력) → 인증 메일 링크 → 인증 → 비밀번호 찾기 → 메일 도착(스팸함 포함) → 링크 → 재설정. 실패는 Render 로그 `비밀번호 재설정 메일 발송 실패`

- 무료 도메인 메일(gmail 등)을 발신 주소로 쓰면 받는 쪽 DMARC 정책 때문에 스팸함으로 갈 수 있음 — 시연 한계로 둠
- 로컬은 `MAIL_SENDER` 기본 `log` → 링크가 앱 로그에 찍힘 (prod에서 log는 기동 실패)
