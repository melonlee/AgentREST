# syntax=docker/dockerfile:1

# ---- build -------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/lazy-rest-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# ---- runtime -----------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system lazyrest && useradd --system --gid lazyrest --no-create-home lazyrest

# One layer per Spring Boot layer: dependencies change rarely, application code often.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER lazyrest
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
