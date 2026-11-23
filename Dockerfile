# 1단계: 빌드 — 테스트는 Testcontainers(Docker)가 필요해 이미지 빌드에서는 제외 (CI에서 실행)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 정의를 먼저 복사해 소스만 바뀐 경우 의존성 다운로드 레이어를 재사용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 2단계: 실행 — JRE만 포함
FROM eclipse-temurin:21-jre
WORKDIR /app

# root가 아닌 사용자로 실행
RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

# 시간대는 SpringDeliveryMiniApplication.main에서 Asia/Seoul로 고정하므로 컨테이너 TZ와 무관
# JVM 옵션은 이미지에 넣지 않고 실행 환경의 JAVA_TOOL_OPTIONS로 지정 (JVM이 직접 읽음)
#   → compose(dev)는 기본값 그대로, 무료 배포(512MB·0.1 CPU, prod-free)만 메모리·기동 옵션 적용 (docs/operations.md 9장)
# 포트: compose·prod는 8080, prod-free는 플랫폼이 주는 PORT (EXPOSE는 문서용이라 실제 포트와 달라도 동작에 영향 없음)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
