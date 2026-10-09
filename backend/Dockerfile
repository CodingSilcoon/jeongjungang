# 운영 이미지. 빌드는 JDK에서, 실행은 JRE만 있는 이미지에서 한다 (테스트는 CI가 돌린다)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src

# 의존성 목록이 그대로면 아래 단계까지 캐시를 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# bootJar만 돌리면 -plain.jar는 만들어지지 않아 jar가 하나다
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q \
    && cp build/libs/*.jar app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/app.jar app.jar
USER app

# 컨테이너 메모리 상한(compose의 mem_limit)의 60%까지만 힙으로 쓴다. 나머지는 메타스페이스·스레드 등 힙 밖 메모리 몫
# (512MB에서 75%로 두면 힙 밖 메모리가 모자라 OOM으로 죽을 수 있다)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
