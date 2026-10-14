# CI에서 만든 bootJar를 실행 전용 이미지에 담는다. 빌드는 서버가 아니라 GitHub Actions에서 한다.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY build/libs/spring-llm-gateway-*-SNAPSHOT.jar app.jar
USER app

# 2GB 서버 기준: 힙 512MB, 컨테이너 메모리 한도 768MB (compose.prod.yaml)
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
