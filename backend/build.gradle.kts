plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"

    // OWASP dependency-check. Replaces nothing in the existing stack: no other tool here knows
    // about published CVEs, and a transitive dependency with a known critical vulnerability is
    // invisible to compilation, to ArchUnit and to every test we have.
    id("org.owasp.dependencycheck") version "12.1.0"
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

dependencyCheck {
    // CVSS 7.0 is the floor of "High". Failing below that would make the build red on advisories
    // nobody intends to act on this week, and a gate people routinely override is not a gate.
    failBuildOnCVSS = 7.0f

    formats = listOf("HTML", "JSON")
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.path

    // The NVD refuses to serve its feed at any useful rate without a key, so a run without one
    // either takes hours or silently analyses an empty database and reports no vulnerabilities.
    // The second failure mode is the dangerous one, which is why CI checks for the key first.
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
    }

    // Test-only dependencies do not ship. Scanning them turns real findings into noise.
    scanConfigurations = listOf("runtimeClasspath")

    analyzers {
        assemblyEnabled = false
        nodeAudit { enabled = false }
        nodeEnabled = false
    }
}
