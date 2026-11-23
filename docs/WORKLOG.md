# 작업 기록 (WORKLOG)

CLAUDE.md에서 옮긴 진행 상황·다음 작업·남은 후속 기록(2026-11-22). 필요한 장만 Grep으로 찾아 읽는다.
최신 상태: #83(소셜 로그인)·#84(develop → main) 머지 완료, `main` = `8b42383`.
- 2026-11-22: CLAUDE.md 작업 기록을 이 파일로 분리. **배포 A안 결정**(Oracle Always Free VM + compose + Caddy, 프론트 Vercel) — 상세 `docs/roadmap.md` 배포 장. 사용자가 이슈·브랜치 생성 후 저장소 쪽 준비 시작 예정
- 2026-11-22 배포 #86(`feat/86-deploy-oracle-vercel`, 미커밋): `deploy/`(compose.prod.yml·Caddyfile·.env.prod.example), `frontend/vercel.json`(API_DOMAIN 자리표시자)·`.env.production`(같은 출처), `social.redirect-base-url`(SOCIAL_REDIRECT_BASE_URL), 프론트 `VITE_STREAM_BASE_URL`·상대 API 주소 지원, `docs/operations.md` 8장. 다음: 사용자 VM·Vercel 작업 → 배포 후 확인(8-7)
  - 전체 테스트 108클래스 949건 통과(Docker 켠 뒤), 프론트 70건·빌드 통과. PR 본문 전달(`Closes #86`, 대상 develop). 포트폴리오 문서(Claude Docs) 갱신: 테스트 949건, 한계 "배포 없음" → "배포 준비 단계(Oracle VM + Vercel, 실제 배포 전)". 배포가 실제로 끝나면 이 문서에 데모 주소를 넣고 한계 문구를 고칠 것
  - `vercel.json`의 `API_DOMAIN` 자리표시자는 VM 공인 IP를 받은 뒤 교체(sslip.io), VM 절차는 대화에서 단계별로 안내함(operations.md 8-2·8-3과 같음)
- 2026-11-23 **배포 방향 전환**: #86 머지(PR #87) 후 Oracle A1 용량 부족이 계속돼 월 $0·카드 없는 무료 구성으로 변경 — Render Free(백엔드) + Neon Free(PG 18) + Upstash Free(Redis) + Vercel, Kafka 제외. 조사·비교·측정은 `docs/operations.md` 9장(8장 Oracle 구성은 A1 확보 시용으로 유지)
  - 구현(미커밋, develop 위): `prod-free` 프로필(`application-prod-free.yml`, `prod,prod-free`로 사용), `event.OutboxSender`(Kafka/Direct) 분리, `ProdProfileGuard` direct 모드 Kafka 주소 면제, Dockerfile 주석(JVM 옵션은 `JAVA_TOOL_OPTIONS`), 프론트 서버 깨우기(`api/serverWake.ts`·`ServerWakeBanner`)
  - 로컬 측정(512MB·0.1 CPU): prod-free 재기동 300초, 메모리 약 320MB. AppCDS는 Render 실측 후 결정(9-4). 다음: 사용자 커밋 → Neon·Upstash·Render·Vercel 생성 → 9-5 확인
- 2026-11-23 **#90 무료 배포 마무리 + 비밀번호 찾기·변경** (브랜치 `chore/90-deployment-demo-data`, 미커밋)
  - 배포 화면 확인: Render는 UP이지만 소셜 키 없음(providers `[]`), main의 `vercel.json`이 `API_DOMAIN` 자리표시자, Neon은 빈 DB, 브랜치 미리보기 주소는 Vercel Authentication
  - 저장소: `vercel.json` Render 주소, `.env.example`(메일·데모 변수), V23(`users.email`, `password_reset_tokens`, `PASSWORD_CHANGED`),
    `PasswordResetService`·`UserService.changePassword/changeEmail`, `mail` 패키지(none·log·brevo), `DemoDataSeeder`(`demo-data.*`), `ProdProfileGuard` 메일 검사,
    프론트 `/password-reset`·로그인 "비밀번호를 잊으셨나요?"·가입 이메일·내 정보 이메일/비밀번호 폼. 문서 `docs/operations.md` 9-2·9-6·9-7, api·architecture·database·erd
  - 사용자 할 일: 커밋·main 반영 → Render 환경변수(소셜·`MAIL_*`·`BREVO_API_KEY`·`PASSWORD_RESET_URL`·`DEMO_*`) → Brevo 발신 주소 인증 → Vercel `VITE_DEMO_ACCOUNTS`·프로덕션 도메인 → 카카오·구글 Redirect URI → 9-5 확인

## 진행 상황 (#78 머지 완료(PR #82) / 소셜 로그인 + 포트폴리오 보강 커밋 완료(`60e0136`)·푸시·PR 전 — 브랜치 `feat/79-social-login`, 2026-11-22)
- 완료: 매장·메뉴·주문, 결제(수수료), 일 정산 집계·확정·지급, 취소·환불·이월(#30), 매장별 수수료율(#32),
  사장님 일 정산 기록 조회(#34), CI·개발 환경(#37), 관측성·API 문서화(#38),
  정산 감사 이력·집계 실패 기록·누락분 재집계(#39 — 기존 정산은 재계산하지 않음, `docs/settlement-policy.md` 10장)
- 목록 API 성능(#40): 페이징(`dto.PageQuery`, 본문은 배열 + `X-Has-Next` 헤더, id 오름차순), N+1 제거, V12 인덱스, k6(`perf/`).
  새 목록 API도 같은 방식 (`docs/performance.md`)
- Redis 결제 멱등성·스케줄러 분산 락(#41): `Idempotency-Key` 헤더(선택) → `service.PaymentIdempotencyService`, 락 → `lock.RedisDistributedLock`.
  Redis 장애 시 키 있는 결제 503, 스케줄러는 실행 건너뜀. compose `redis` 127.0.0.1:6380 (호스트 6379는 다른 프로젝트 → 건드리지 않음)
- Kafka Transactional Outbox·알림(#42, V13·V14): `event.OutboxEventRecorder`(MANDATORY) → `outbox_events` → `event.OutboxPublisher`(SKIP LOCKED, 백오프, FAILED)
  → `delivery-events` → `NotificationService`(`consumed_events` 멱등성) → `notifications`, 실패는 `delivery-events-dlt`.
  DB 결제 멱등성 기록(`payment_idempotency_keys`), FAILED 재발행(`/api/admin/outbox-events`), 알림 발송(`event.NotificationDispatcher`, 기본 로그)·
  내 알림 조회, 보관 기간 정리(`maintenance.RetentionCleaner`), 메트릭 + `monitoring/alerts.yml` (`docs/architecture.md` Redis·Kafka 장)
  - compose `kafka` 127.0.0.1:9094. test 프로필은 Kafka·알림 발송·정리 주기 끔 → Kafka 테스트만 `KafkaTestcontainersConfiguration`으로 켬
  - 멱등성 키를 쓰는 통합 테스트는 `payment_idempotency_keys`를 payments보다 먼저 삭제
- DLT 재처리·카운터(#52): CLI `event.DltReprocessRunner`(`dlt-reprocess` 프로필, `server.port: -1`) → `event.DltReprocessor`,
  `outbox.dlt.records`는 DLT 전송 성공 후 증가 (`docs/kafka-outbox.md` 7장)
- 결제 단건 조회(#53): `GET /api/payments/{id}` (CUSTOMER 본인만, 타인 403·없음 404)
- 주문 목록 keyset·운영 프로필·DB 타임아웃(#54): `GET /api/orders?afterId=&size=` (`dto.KeysetQuery`, 헤더 `X-Next-After-Id`),
  `prod` 프로필(`config.ProdProfileGuard`, 관리 포트 8081), hikari 풀·`lock_timeout`·`statement_timeout` → 503 (`docs/operations.md`)
  - 통합 테스트에서 prod를 켤 때는 `@ActiveProfiles({"test","prod"})` + 필수 환경변수 더미 값 (`ProdProfileIntegrationTest`)
  - 오래 걸리는 마이그레이션은 파일 안에서 `SET LOCAL statement_timeout = 0`
- 가맹점 정산 내역 조회(#59, 머지 완료): `GET /api/settlements?storeId=&status=&from=&to=&page=&size=`
  (`controller.SettlementController` → `OwnerSettlementService.getMySettlements`, `dto.MySettlementResponse`)
  - 본인 매장 id를 먼저 정한 뒤 `store_id IN (...)` + Specification(선택 조건은 값 있을 때만), id 오름차순 페이징
  - 인덱스 추가 없음(V15 미사용) — 근거는 `docs/database.md` settlements 장 (EXPLAIN 실측 아님)
- CORS·로컬 수동 검증(#61, 머지 완료): `SecurityConfig.corsConfigurationSource()` + `config.CorsProperties`
  (`CORS_ALLOWED_ORIGINS`, 와일드카드·경로 금지, credentials 끔, local·dev 기본 5173·3000, prod 필수 → `ProdProfileGuard`).
  Preflight는 `CorsFilter`가 인증 전에 처리(OPTIONS permitAll 없음). 근거 `docs/operations.md` 7장, 수동 결과 `docs/local-verification.md` 2-2·2-3·2-5·3장 재검증·4장
  - `ProdProfileGuard`를 빈 → `EnvironmentPostProcessor`(`META-INF/spring.factories`)로 변경: 빈 생성 전 검사라 DB_URL 누락 시에도 빠진 변수 목록이 나옴 (`ProdProfileGuardStartupTest`)
- 주문 단건·내 결제 목록·내 정보(#62, 머지 완료):
  `GET /api/orders/{id}`(`dto.OrderDetailResponse` = 주문 + `payment`(결제 전 null), CUSTOMER 본인·OWNER 본인 매장, ADMIN 403, fetch join → 쿼리 3번),
  `GET /api/payments?page=&size=`(`PaymentService.getMyPayments`, `findAllByOrderUserId`), `GET /api/users/me` → `dto.MyInfoResponse`(DB 기준 role, OWNER `storeIds`, 그 외 `[]`)
  - 인덱스 추가 없음(V15 미사용) — 근거 `docs/performance.md` 10장 (EXPLAIN 실측 아님)
- 알림 읽음·Refresh Token(#63, V15·V16, develop 머지 완료 — PR #66):
  - 알림: `notifications.read_at`, `PATCH /api/notifications/{id}/read`(본인만, 멱등 204, 타인 403·없음 404), `GET /api/notifications/unread-count`.
    `read_at`은 엔티티에서 `insertable·updatable=false` + 조건부 UPDATE(`markReadIfUnread`) — 발송기의 전체 컬럼 UPDATE와 서로 덮어쓰지 않도록
  - 인증: `refresh_tokens`(해시만, `family_id`), `service.AuthTokenService`, `POST /api/users/refresh`·`/logout`(permitAll, 쿠키로 인증),
    HttpOnly 쿠키 `refresh_token`(Path=/api/users, SameSite·Secure는 `auth.refresh-token.*`) → CORS `allowCredentials=true`로 변경(#61 설계 변경)
    재사용 시 계열 전체 폐기(엄격), 동시 재발급은 `FOR UPDATE`로 1건만 성공. 401 본문 `code`(`exception.ErrorCode`) 추가 — 근거 `docs/architecture.md` 인증 장
  - 사용자 결정(2026-11-20): 저장소 DB / 전달 HttpOnly 쿠키 / 재사용 엄격 / 401 구분은 `code` 필드
  - 통합 테스트에서 로그인 API를 쓰면 `refresh_tokens`를 users보다 먼저 삭제
  - V16 후속 처리(2026-11-20, 사용자 결정): Access 15분(`jwt.expiration-ms`, 즉시 차단은 안 함 — DB·Redis 확인 대안은 `docs/architecture.md` 인증 장),
    계열 절대 만료 30일(`family_expires_at`, `absolute-ttl`), 만료 후 7일 지난 토큰 정리(`RetentionCleaner`, `retention.refresh-tokens`)
- 모니터링 확인(#67, 머지 완료 PR #72): 대시보드 18패널·4행(알림 상태 `ALERTS` 표·Outbox·Kafka·알림·Redis·스케줄러),
  규칙 4개 firing 실측(`docs/local-verification.md` 5장). 발견·수정: 락 초과 카운터 기동 시 0 등록(`DistributedLock.OVERRUN_METRIC`),
  lag 규칙 `records_lag_max`(NaN) → `records_lag`, HTTP 503 패널은 누적값(처음 생기는 시계열은 `increase()`가 첫 1건을 놓침)
- k6 쓰기 경로 부하 테스트(#68, 머지 완료 PR #73): `perf/k6/order-payment.js`(SCENARIO=load|idempotency),
  `perf/compose.perf.yml`(compose 앱 → `delivery_perf` DB), k6는 Docker 이미지로 compose 네트워크 안에서 실행(호스트 k6 없음). 결과 `docs/performance.md` 11장
  - 상한 약 300 흐름/s = 노트북 CPU(풀 20도 동일), 멱등성 동시 50건 × 20회 중복 0, 키 비용 결제당 약 10ms
  - 별도 이슈 후보(미등록): Outbox 발행 상한 약 92건/s(batch 100 + fixed-delay 1s) < 결제 속도, 발행 쿼리가 PUBLISHED를 PK 순서로 걸러냄
- Kafka 알림 Slack 발송(#69, 머지 완료 PR #74): `event.SlackNotificationSender`(JDK HttpClient, 연결 2s·응답 3s),
  `notification.sender`=log(기본)|slack, `SLACK_WEBHOOK_URL`(기본값 없음, prod는 `ProdProfileGuard`가 slack일 때만 필수). 실패는 `SlackSendException` → 기존 재시도·FAILED.
  문구는 notification id·이벤트 종류만(운영 채널 1곳 데모). URL은 예외 메시지·toString·로그에 넣지 않음. 실제 Slack 수신 확인 완료(2026-11-20, `docs/local-verification.md` 2-6 1~5단계 통과)
- 사장님 실시간 주문 알림(#76, 머지 완료 PR #80, 2026-11-21): `GET /api/owner/order-events`(SSE, OWNER, 선택 `storeId`)
  - `NotificationService.handle`이 사장님 알림(결제 완료·환불) 저장 시 `event.OwnerOrderNotified` 발행 → 커밋 후 `event.OwnerOrderStreamRegistry`(메모리, 전용 전송 스레드·대기열, 하트비트 25s, 사용자당 5연결, 10분 만료, `SmartLifecycle`로 graceful shutdown 전 정리)
  - **open-in-view 함정**: SSE 응답 동안 커넥션 점유 → `spring.jpa.open-in-view=false` + `config.OpenEntityManagerInViewConfig`(SSE 경로만 제외하고 재등록). REQUIRES_NEW로는 안 됨(테스트로 확인)
  - 오류 응답 Content-Type JSON 고정(`GlobalExceptionHandler.jsonError`) — `Accept: text/event-stream`의 403·404가 500이 되던 문제. `DispatcherType.ASYNC` permitAll
  - 프론트: fetch 스트림(`api/orderStream.ts`, 토큰을 URL에 넣지 않음) + `owner/orderStreamController.ts`(백오프 재연결, 연속 3회 실패 → 30초 폴링, 60초 무응답 감지) + 토스트·'알림 소리 켜기'(Web Audio)
  - 다중 인스턴스는 Redis Pub/Sub 후속(문서만). compose(Kafka 경유) 실제 확인 완료 — 결제 → SSE 2.3초, SSE 유지 중 hikari 활성 0 (`docs/local-verification.md` 6장). 브라우저 확인은 아직
  - 하트비트 빈은 `config.OwnerStreamConfig`(BackgroundTaskConfig에 두면 `NotificationSenderConfigTest` 단독 컨텍스트가 깨짐)
  - compose 앱은 이번 확인 때 `NOTIFICATION_SENDER=log`로 재생성됨(`.env`는 slack 그대로)
- 주소·결제 예외 처리(#77, 머지 완료 PR #81, V18·V19): `entity.DeliveryAddress`(@Embeddable, 생성 시 검증 → `InvalidAddressException` 400 `INVALID_ADDRESS`),
  요청은 `zipCode`·`roadAddress`·`detailAddress`(한 줄 `deliveryAddress` 요청은 안 받음), `orders.delivery_address`는 한 줄 주소로 계속 채움(기존 주문은 세 컬럼 NULL, 백필 안 함)
  - 오류 코드: `ErrorCode`에 주문·결제 코드 추가, `Invalid(Order|Payment)StatusException`에 code(기본 `INVALID_*_STATUS`). 표는 `docs/api.md` 공통 사항
  - 프론트: `components/AddressFields`(다음 우편번호 embed, `t1.kakaocdn.net`, 실패 시 직접 입력), `address/validateAddress`, `api/errorMessages`(code별 문구 — `ErrorMessage`·토스트)
  - 랜딩(`/`, `pages/LandingPage` + `landing/*` + `styles/landing.css`, 고객·사장님 공식 홈페이지): 비로그인만 표시, 로그인 시 역할별 이동. 매장 목록은 `/stores`(고객 첫 화면도). 애니메이션 라이브러리 없이 IntersectionObserver + CSS. 상세 `frontend/README.md` 랜딩 절
  - 고객 매장 목록(`/stores`) 개선: 카드·검색(매장+메뉴)·필터·정렬·상태 화면, 모두 프론트 처리(서버 API 변경 없음). 카테고리·이미지·영업 상태는 API에 없어 미표시. 상세 `frontend/README.md` 고객 매장 목록 절
  - 체험용 주소·배달 요청사항(V19): 장바구니 기본은 체험용 주소 `(00000) 체험용 주소 (실제 배달되지 않음)`(데모에 실제 집 주소가 저장되지 않도록, 프론트만), `orders.delivery_request varchar(100)`(선택, Order 생성자에서 검증 → 400 `INVALID_DELIVERY_REQUEST`), 고객 주문 상세·사장님 홈 티켓·주문 관리에 표시
  - 로컬 compose 앱(8091)은 V17 이미지라 새 주문 형식을 모름 → 다시 빌드해야 실제 주문 가능(빌드 시 로컬 DB에 V18·V19 적용)
  - 테스트용 주소는 `TestAddresses.seoul()`. 브라우저 확인은 아직(`docs/local-verification.md` 7-2)
- 토스페이먼츠 테스트 모드(#78, 브랜치 `feat/78-toss-payments`, 미커밋·PR 본문 작성 완료, V20·V21 — 이슈 본문의 V19는 이미 사용돼 V20): 상세 `docs/api.md` 토스페이먼츠 결제 장, 구조 `docs/architecture.md` 토스페이먼츠 장
  - `GET /api/payments/toss/config`·`POST .../checkout`(저장 없음, `order-{id}-{난수16}`)·`POST .../confirm`(`PaymentIdempotencyService.confirmToss` → `TossPaymentService`, @Transactional 없음)
  - 승인: 금액·주문번호·주문 상태를 토스 호출 **전에** 확인, 토스 멱등 키 `confirm-{paymentKey}`, `payments.pg_payment_key` UNIQUE, 결과 불명 → 조회 → 그래도 모르면 503 `PG_RESULT_UNKNOWN`(취소 안 함), 승인 후 기록 불가(주문 취소 등) → 보상 취소
  - 취소: 주문 취소·관리자 환불이 `PgCancelService.requestCancel`(MANDATORY)로 `pg_cancel_requests` 기록 → 커밋 후 `PgCancelProcessor` 1회 + 주기 재시도(`config.TossPaymentsConfig`, test 프로필은 끔). 멱등 키는 시도마다 다름(오류 응답도 재전송되므로), 중복 환불은 전액 취소 + `ALREADY_CANCELED_PAYMENT`=DONE
  - 키는 `TOSS_CLIENT_KEY`·`TOSS_SECRET_KEY`(.env만, `test_` 키만 허용, 둘 다 없으면 꺼짐 → 모의 결제). 모의 결제 `POST /api/payments`는 그대로
  - 통합 테스트에서 토스 결제를 만들면 `pg_cancel_requests`를 payments보다 먼저 삭제. 토스 클라이언트는 `@MockitoBean`
  - 실제 결제창 확인은 아직(`docs/local-verification.md` 8-2)
  - 같은 브랜치 추가(2026-11-21, 사용자 요청): **부분 환불(V21)** — `payment_refunds`(결제 1 : 환불 N, 주문-결제 1:1은 유지), `POST /api/admin/payments/{id}/refunds`, 수수료는 `PaymentFee.refundPortion`(누적 기준 반올림), 정산 차감 단위 = 환불 1건(`settlement_refunds.payment_refund_id` UNIQUE, 기존 전액 환불 백필), 토스 부분 취소는 `balance_after`로 재시도 전 잔액 확인 + 같은 결제 순서대로. 사용자 결정: 금액 지정 / 수수료 비율 / 환불 기록만 1:N
    - 통합 테스트에서 환불을 만들면 `settlement_refunds` → `payment_refunds` → payments 순서로 삭제
  - **가짜 PG 모드** `TOSS_MODE=fake`(`pg.FakeTossPaymentsClient` + `FakeTossCheckoutController` + 프론트 `/payments/fake-toss`): 토스 미접속 시연·장애 재현(응답 유실·PG 지연 등), prod 기동 불가(`ProdProfileGuard`). `TossPaymentsClient`는 인터페이스(real = `TossPaymentsHttpClient`)
  - **compose Redis 503 5초 원인 해결**: Netty DNS 조회(Docker DNS NXDOMAIN 약 4초)가 connect-timeout 밖 → `config.RedisClientConfig`로 DNS 질의 상한 1초, 실측 1.02~1.04초
  - V20은 미적용 상태에서 `pg_cancel_requests` 정의를 고침(같은 브랜치·미커밋). 로컬 compose 앱은 V21 이미지 + `TOSS_MODE=fake`로 재생성(8091), 로컬 DB V21 적용, 측정용 주문 517~521 남음
- 소셜 로그인(#79, 브랜치 `feat/79-social-login`, V22, 커밋 `60e0136`): 상세 `docs/architecture.md` 소셜 로그인 장, API `docs/api.md` 소셜 로그인 절
  - 사용자 결정(2026-11-21): 기존 회원은 로그인 후 직접 연결(이메일 매칭 없음) / 성공 시 Refresh 쿠키만 → 프론트 `/oauth/callback`에서 refresh
  - `social` 패키지: `SocialProvider`(확장 지점, 카카오 OAuth2 `id`·구글 OIDC `sub`), 시작 기록은 세션 대신 서명 쿠키(`CookieAuthorizationRequestRepository`, Lax·10분), 연결 의사는 `social_link` 쿠키(연결 API가 발급, 5분, `?mode=link`)
  - 키 `KAKAO_*`·`GOOGLE_*`(.env, 둘 다 없으면 꺼짐 → 404·버튼 숨김), `SOCIAL_FRONTEND_CALLBACK_URL`(prod는 키 있으면 필수). 관리자 연결 403, 비밀번호 없는 회원의 마지막 계정 해제 409
  - 통합 테스트에서 소셜 회원을 만들면 `social_accounts`를 users보다 먼저 삭제. 공급자 통신은 `OAuth2AccessTokenResponseClient`·`OAuth2UserService` 테스트 빈으로 대체(`SocialLoginIntegrationTest`)
  - 실제 공급자 확인(2026-11-21): 키 `.env` 설정 완료, compose 앱 V22 재빌드(8091), 사장님 `fc110847`에 구글·카카오 연결 통과(`social_accounts` 2행) — **이 테스트 데이터는 남겨 둠(사용자 요청)**. 나머지 시나리오는 `docs/local-verification.md` 9-3
  - 프론트 후속(같은 브랜치): 가입 화면에도 소셜 버튼(`variant="signup"`), 공급자 가이드 모양(`.btn--kakao`·`.btn--google` + `components/SocialLogo`), 카카오 먼저
- 포트폴리오 보강(2026-11-22, 같은 브랜치, 사용자 요청): README 상단 "핵심 요약" 표·회원 행에 소셜 로그인 한 줄(사례로는 안 넣음),
  EXPLAIN ANALYZE 실측(`delivery_perf`) → `docs/performance.md` 10장·`docs/database.md` settlements 장. **발견**: 내 결제 목록은 결제 많은 고객의 뒤쪽 페이지에서 custom plan이 결제 전체 Seq Scan(약 50ms, generic 5.9ms) → 코드 변경 없이 후속 후보(README 후속 개선), 정산 조회는 판단 유지(최대 6.2ms)
  면접 대비는 Claude Docs `Spring-Delivery-mini 이력서·포트폴리오`(https://claude.ai/artifact/C1wUY49pTnSuVZPSybJG8F) 끝에 "다른 방법을 고르지 않은 이유"·"수치를 물으면" 추가
  - 스크린샷(2026-11-22, 사용자 촬영, `docs/images/` 6장 — `grafana (1).png`는 `grafana-outbox.png`로 이름 변경): README "화면" 절(4장 표 + Grafana), GIF는 아직. 촬영 중 발견한 고객 주문 상세 '배달완료 -' 표시는 배달 시각 있을 때만으로 수정
  - **배포는 Vercel로 결정(사용자, 2026-11-22), 별도 작업**: Vercel은 프론트(정적 Vite)만 — 백엔드(Spring·PostgreSQL·Redis·Kafka)는 다른 곳 필요. 다른 도메인이면 Refresh 쿠키 `SameSite=None`+Secure, `CORS_ALLOWED_ORIGINS`, 소셜 콜백·콘솔 Redirect URI, SPA 새로고침용 rewrite(`vercel.json`) 확인
- 검증 기준: 전체 테스트 108개 클래스 946건 통과(2026-11-21, 소셜 로그인 포함) + 프론트 `npm test` 70건·`npm run build`(2026-11-21)
- 포트폴리오: README "문제 해결 사례" 7건 + "핵심 요약"·"화면"(2026-11-22, 미커밋). 이력서·면접 질문은 Claude Docs `Spring-Delivery-mini 이력서·포트폴리오`(https://claude.ai/artifact/C1wUY49pTnSuVZPSybJG8F) — 2026-11-22 갱신: 사례 5~7, 테스트 108/946, 소셜·토스·SSE·화면, 한계(재결제·SSE 단일 인스턴스·결제 목록·배포 없음), "다른 방법을 고르지 않은 이유"·"수치를 물으면". 기능이 바뀌면 이 문서도 같이 갱신

## 다음 작업 — 프론트엔드 연동 전 백엔드 (2026-11-18 이슈 초안 작성 → 사용자가 직접 등록, 번호 받으면 아래에 기입)
1. ~~CORS 설정~~ #61
2. ~~주문 단건 조회~~ #62
3. ~~내 결제 목록 조회~~ #62
4. ~~알림 읽음 처리~~ #63 (V15)
5. ~~내 정보 조회 개선~~ #62 (`MyInfoResponse`)
6. ~~토큰 만료 대응~~ #63 Refresh Token (V15·V16)
7. ~~로컬 수동 확인~~ 완료(2026-11-18, `docs/local-verification.md` 각 절 검증 결과). 발견한 `ProdProfileGuard` 실행 순서 문제는 #61에서 수정
- **남은 작업 (2026-11-20 이슈 등록, 견적 `docs/roadmap.md`, 합계 약 15~20일)**: 순서대로
  #67 모니터링 확인(1~1.5일) → #68 k6 쓰기 경로(2~3일) → #69 Kafka 알림 Slack 발송(1.5~2일) → #70 프론트엔드(10~14일)
- 프론트엔드 (#70, 머지 완료 PR #75): React 19+TS+Vite 7+Router 7. **디자인 적용 완료(2026-11-21)** — 점주 콘솔 V3
  (claude.ai 디자인 캔버스 https://claude.ai/artifact/JuqJVeGSKzRNPuGMXM98tF: 로그인·홈·주문·정산 + 디자인 시스템).
  구조·디자인과 다른 점·성능 기본값·알려진 제약은 `frontend/README.md`. 테스트 `npm test`(19건), 빌드 `npm run build`
  - 사장님 화면: `/owner`(홈) · `/owner/orders` · `/owner/settlements` · `/owner/stores`(매장·메뉴), 공통 상태 `owner/OwnerContext`
  - 주문 목록 API가 id 오름차순뿐이라 사장님 화면은 전체 주문을 keyset으로 끝까지 받음(상한 5,000) → 백엔드 후보: 최신순·기간 필터, 오늘 집계 API
  - 백엔드 후보: `GET /api/menus?storeId=` (지금은 매장 상세에서 전체 메뉴를 받아 거름)
  - 고객·관리자 화면은 디자인이 없어 구조 유지 + 공통 클래스만 디자인 시스템 모양. 관리자 거절 사유는 아직 `window.prompt`
  - 테스트 계정 버튼은 `VITE_DEMO_ACCOUNTS`(역할:아이디:비밀번호) 있을 때만 — 로컬 `frontend/.env`에 OWNER·ADMIN 설정
  - 더미 데이터(2026-11-21, 로컬 `delivery` DB): `fc110847` 매장 4 `샘플매장 성수점`(이름은 SQL로 변경)·매장 5 `샘플매장 판교점`, 메뉴 10개,
    고객 `demo70c1`·`demo70c2`(password123), 지난 30일 주문 467건(SQL 삽입, 수수료 0.05) + 정산 60건(재집계 API → D-1·D-2 PENDING, D-3·D-4 CONFIRMED, 나머지 PAID — 지급 시각은 모두 실행 시점),
    오늘 주문 18건(API 흐름: 배달완료·취소·배달 중·수락 대기·결제 전, 주문 시각만 SQL로 11:10~ 분산). 브라우저 화면 확인은 아직
  - 사장님 승인제(V17, 2026-11-21 사용자 결정): 가입은 CUSTOMER만(`role` 선택값, OWNER·ADMIN 400) → `POST /api/owner-applications` →
    관리자 `PATCH /api/admin/owner-applications/{id}/approve|reject` → 승인 시 같은 트랜잭션에서 `User.promoteToOwner()`.
    `owner_applications` 부분 유니크(대기 1건), 신청 행 `FOR UPDATE`, 진행 중 주문 있으면 신청·승인 409. 기존 OWNER 계정은 그대로.
    재발급 전 Access는 CUSTOMER라 사장님 API 403 → 프론트 '사장님 화면으로 전환'이 refresh + `/me`. 근거 `docs/api.md` 사장님 전환 신청
    - 통합 테스트에서 신청을 만들면 `owner_applications`를 users보다 먼저 삭제
  - CI 경로 분리(2026-11-21): `ci.yml`은 `src/**`·gradle 파일만, `frontend.yml`은 `frontend/**`만 (트리거 브랜치는 main 그대로)
  - 연동 규칙은 `docs/api.md` 공통 사항·"인증 토큰" 절이 기준: Access는 메모리 보관 + Bearer, `login`·`refresh`·`logout`만 `credentials: 'include'`,
    앱 시작 시 refresh 1회, 401은 `code`로 분기(`TOKEN_EXPIRED`만 재발급), 재발급은 single-flight(중복 재발급 = 재사용 감지 → 로그인 전체 폐기)
  - 로컬: 프론트 5173(CORS 기본 허용) → API `http://localhost:8090`
- **다음 세션 시작점 (2026-11-22 갱신)**: #76·#77·#78 머지 완료(PR #80·#81·#82). 브랜치 `feat/79-social-login`에 소셜 로그인(V22) + 포트폴리오 보강(README·실측·스크린샷·프론트 소셜 버튼·주문 상세 표시 수정)을 **사용자가 커밋 완료(`60e0136`, develop보다 1커밋 앞, 원격 브랜치 없음)**. 마무리 확인(2026-11-22): 백엔드 108클래스 946건·프론트 70건·빌드 통과, `.env`류 커밋 제외 확인. PR 본문은 작성해 전달함(파일은 사용자 요청으로 삭제 — 필요하면 다시 작성, #82와 같은 구성, `Closes #79`).
  **대기 중**: 사용자의 푸시·develop PR(develop 대상 PR은 CI가 안 돎 — 트리거가 main뿐), 마무리로 develop → main PR 권장(`origin/main`은 #58에 멈춰 있음, 그때 CI 전체 실행), 실제 소셜 로그인 남은 시나리오(9-3 — 구글·카카오 연결은 통과), 사장님 실시간 알림 GIF(`docs/images/owner-realtime.gif` → README "화면" 맨 위), `customer-order-detail.png` 재촬영(수정 전 '배달완료 -' 문구가 찍혀 있음), Vercel 배포(별도 작업, `docs/roadmap.md` 마지막 장), `.env`의 `APP_PORT`를 8091로 바꿀지(지금 8080 → 그대로 `docker compose up`하면 포트 충돌로 앱이 Created에 멈춤).
  소셜 로그인 후속 후보: ~~소셜 전용 회원 비밀번호 설정~~(V23), 회원 합치기(필요 시). 새 작업은 사용자 지시 대기.
  #78 이전 기록: 수동 확인(8-2 실제 토스 결제창, 8-3 가짜 결제창 브라우저)은 아직.
  남은 #78 후속: 관리자 부분 환불 화면(지금은 API만), 토스 대사(미기록 승인 찾기), 고객 화면 환불 진행 상태 표시.
  로컬: compose 앱 `APP_PORT=8091`(V22 이미지, `TOSS_MODE=fake`·`NOTIFICATION_SENDER=log` — 셸에서 `$env:APP_PORT='8091'; $env:TOSS_MODE='fake'; $env:NOTIFICATION_SENDER='log'` 후 `docker compose --profile monitoring up -d app`), 로컬 DB V22, 프론트 `frontend/.env` → 8091(테스트 계정 버튼 3개), Grafana http://localhost:3000/d/delivery-app/delivery-app. 소셜 키는 `.env`에 설정됨(콘솔 Redirect URI는 8091).
  확인용 계정: 관리자 `adm6118974`, 승인된 사장님 `fc110847`(매장 4), 고객 `demo70c1`, 비밀번호 모두 password123. 측정용 주문 517~521(519는 가짜 PG로 결제됨), 스크린샷용 주문 522~(가짜 PG). **로컬 테스트 데이터·계정은 지우지 않음(사용자 요청, 2026-11-21)** — `fc110847`에 구글·카카오 연결됨. 사용자 판단 대기: CI 트리거에 develop 추가 여부
- (2026-11-20 기록) #59·#61·#62·#63 develop 머지 완료 → 프론트엔드 연동 전 백엔드 목록 완료.
  남은 작업 이슈 #67~#70 등록·`docs/roadmap.md` 작성. #67~#70 모두 머지 완료(PR #72·#73·#74·#75)
  - **#69 실제 Slack 수신 확인 완료(2026-11-20)**: `docs/local-verification.md` 2-6 1~5단계 통과·기록. 루트 `.env`는 `NOTIFICATION_SENDER=slack` 상태(되돌릴지는 사용자 판단).
    호스트 8090도 다른 프로젝트(mock-bank)가 사용 → compose 앱은 `APP_PORT=8091`. 다음은 #70
  - V15 후속 3건은 V16에서 처리. 남은 것: 로그아웃 후 Access 최대 15분 유효(즉시 차단 필요 시 요청마다 DB 확인), 다른 사이트 배포 시 SameSite=None
  - 별도 등록 대기(#61에서 발견): ① ~~compose 앱에서 Redis 중단 시 키 있는 결제 503이 약 5초~~ 원인 확인·해결(#78 브랜치, DNS 조회)
    ② CORS 거부 403 본문이 공통 오류 형식 아님(영향 낮음)
  - 로컬 상태: Docker Desktop 재시작 후 compose db·redis·kafka·app·prometheus·grafana 모두 실행 중(2026-11-19), 검증용 주문·결제·알림 데이터가 로컬 DB에 남아 있음
    (#61 검증 회원 `own61*`·`cus61*`·`adm61*`(ADMIN), 비밀번호 password123, 매장 2·주문 12~24 — #67에서 주문 22~24·DLT 테스트 메시지 키 967 추가). db 컨테이너는 #61에서 재생성(볼륨 유지).
    #68 측정 전용 DB `delivery_perf`(같은 db 컨테이너, seed 30만 + 결제 약 5.4만)가 남아 있음 — 앱은 원래 `delivery`로 되돌려 둠.
    호스트 8080은 다른 프로젝트(logistics-msa)가 사용 → 로컬 실행은 `SERVER_PORT=8090`, compose는 `APP_PORT=8090`

## 남은 후속 (프론트 이후)
- **다음 작업 4건 (2026-11-21 사용자 결정, 견적·상태 `docs/roadmap.md` 마지막 장)**: 순서대로, 각각 별도 이슈·브랜치 (#76·#77·#78, 4번은 이슈 번호 미정)
  1. 사장님 실시간 주문 알림 **#76** (머지 완료 PR #80)(1.5~2일): 기존 `PAYMENT_COMPLETED` 사장님 알림 저장 → SSE로 접속 중인 사장님에게 전달, 프론트 주문 갱신·토스트·소리(자동 재생 정책 → '알림 소리 켜기'), 재연결·폴링 대체
  2. 주소·결제 예외 처리 **#77** (머지 완료 PR #81)(1~1.5일): 주소 우편번호·도로명·상세 분리(다음 우편번호, V18), 결제 오류 코드 정리·화면 문구
  3. 토스페이먼츠 테스트 모드 **#78** (머지 완료 PR #82 — 부분 환불·가짜 PG·Redis DNS 수정 포함)(2~3일): **공식 문서 공개 테스트 키**, 서버 승인 시 금액을 주문 총액과 대조, 결제 키 저장(V20 — V19는 배달 요청사항에 사용), 취소 연동
  4. 소셜 로그인 (브랜치 `feat/79-social-login`, 구현·자동 테스트 완료·미커밋, 실제 공급자 확인 대기)(2.5~4일): **카카오 + 구글**(공급자 2개 이상 구조), 성공 시 기존 Refresh 쿠키 발급, 소셜 계정 연결 테이블(V22), 신규는 CUSTOMER. 키는 사용자가 발급해 `.env`에
- 개선 후보: DLT 재처리 CLI 강제 종료 시 남는 락 `lock:dlt-reprocess`(TTL 30분, 종료 훅 해제 또는 짧은 TTL + 연장, `docs/kafka-outbox.md` 7-3)
- 그 밖: Alertmanager 연동, Redis 고가용성, 이벤트 테이블 파티셔닝(대용량 시),
  ~~부분 환불~~(#78 브랜치, V21 — 환불 기록 1:N로 해결, 주문-결제 @ManyToOne 전환은 재결제가 필요할 때), 주간·월간 지급, 외부 연동(실제 알림 발송 채널·송금)은 마지막

