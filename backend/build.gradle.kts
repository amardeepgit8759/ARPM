plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.placefy"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")

    // Replaces hand-walking the Map<String, Object> that snakeyaml (already on the classpath
    // via Spring Boot) returns. Binding taxonomy content to records lets an unknown or missing
    // field fail at the parse boundary with the offending field named, instead of surfacing as
    // a null deep inside the loader. Version is managed by the Spring Boot BOM.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("net.jqwik:jqwik:1.10.1")
}

// `test` is the fast suite: domain, use cases, web slices and ArchUnit.
// Anything needing a real PostgreSQL is tagged "integration" and runs in `integrationTest`,
// so CI can report the two separately without a second source set.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs tests that require a real PostgreSQL via Testcontainers."
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named("check") {
    dependsOn(integrationTest)
}
