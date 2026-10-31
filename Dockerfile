# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so source changes do not re-download them.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/enrollment-service.jar extract --layers --launcher --destination extracted

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --uid 10001 --no-create-home app
WORKDIR /app

# Least to most frequently changing, so a code change only replaces the last layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
