# ---- Build stage ----
# 프로젝트가 Gradle wrapper로 9.5.1을 고정하고 있어(gradle/wrapper/gradle-wrapper.properties)
# 도커 허브의 gradle:8-* 이미지(번들 Gradle 8.x)를 쓰면 버전이 어긋난다. JDK만 있는
# 이미지 위에서 ./gradlew를 그대로 실행해 wrapper가 지정한 버전을 받아쓰게 한다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
# 의존성 레이어 캐시 (소스만 바뀌었을 땐 이 레이어가 재사용되어 재다운로드를 건너뜀)
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
# 배포 이미지 빌드 시점에는 테스트를 돌리지 않는다 — 테스트는 CI에서 별도로 검증하고,
# 여기서는 빌드 속도를 우선한다.
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
# JRE가 아니라 JDK인 이유: LocalProcessExecutor가 AI 생성 JAVA 답안코드를
# `java Main.java` 단일 파일 소스 실행(JEP 330)으로 검증하는데, 이 기능은 JDK에만 있다.
FROM eclipse-temurin:21-jdk
WORKDIR /app

# C/Python 답안코드 채점용 컴파일러·인터프리터 설치.
# LocalProcessExecutor는 논윈도우 환경에서 "python3"을 직접 호출하므로(pythonCmd())
# python 심볼릭 링크는 만들지 않는다 — 실제로 쓰이지 않는 별칭을 유지할 이유가 없다.
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    python3 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
