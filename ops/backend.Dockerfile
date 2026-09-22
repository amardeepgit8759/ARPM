# Build context is the repository root, so this can see backend/ .

FROM gradle:8.14.5-jdk17-alpine AS build
WORKDIR /build

# Dependencies first: they change far less often than source, so an edit to a Java file
# re-uses this layer instead of re-resolving the whole graph.
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
RUN gradle --no-daemon dependencies --quiet || true

COPY backend/src ./src
RUN gradle --no-daemon bootJar --quiet

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Not root. A container that does not need to write to its own filesystem should not be able to.
RUN addgroup -S placefy && adduser -S placefy -G placefy
USER placefy

COPY --from=build --chown=placefy:placefy /build/build/libs/*.jar app.jar

# Reviewed YAML content, read at startup when taxonomy seeding is enabled. Copied rather than
# baked into the jar so content can be inspected in a running container without unpacking it.
COPY --chown=placefy:placefy content /app/content

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
