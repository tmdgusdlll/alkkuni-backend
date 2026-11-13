# api·batch 공통 이미지. jar는 CI(또는 로컬)가 ./gradlew :{모듈}:bootJar로 미리 만든다.
# 빌드: docker build --build-arg MODULE=api -t alkkuni-api .
# 프로필(SPRING_PROFILES_ACTIVE)·메모리 제한·헬스 체크는 운영 compose(compose.prod.yaml)에서 정한다.
FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
# data/raw: batch 원본 저장 볼륨의 마운트 지점. 이미지에 app 소유로 만들어 둬야 새 볼륨도 app이 쓸 수 있다.
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/data/raw && chown -R app:app /app
COPY --chown=app:app ${MODULE}/build/libs/${MODULE}.jar app.jar
USER app
EXPOSE 8080
# 힙은 컨테이너 메모리 제한(mem_limit)의 75%. 제한이 없으면 호스트 메모리 기준이 되니 compose에서 꼭 건다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
