package com.placefy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/**
 * The architecture rules that are worth failing a build over.
 *
 * <p>These are not style preferences. Each one protects a property the product depends on: that
 * the domain can be reasoned about without a container, that dependencies point inward, and — the
 * one that matters most for what Placefy is — that the layer which computes results cannot read a
 * clock or a random source.
 */
@AnalyzeClasses(packages = "com.placefy", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String DOMAIN = "com.placefy.domain..";
    private static final String APPLICATION = "com.placefy.application..";
    private static final String INFRASTRUCTURE = "com.placefy.infrastructure..";
    private static final String WEB = "com.placefy.web..";

    // ---------------------------------------------------------------- framework isolation

    @ArchTest
    static final ArchRule domainHasNoFrameworkDependencies = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..",
                    "com.nimbusds..",
                    "org.flywaydb..")
            .because("the domain must be readable, testable and portable without any framework on the classpath");

    @ArchTest
    static final ArchRule applicationHasNoFrameworkDependencies = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax..", "org.hibernate..", "com.fasterxml.jackson..")
            .because("use cases are plain objects wired by hand in infrastructure/config, so a use-case test "
                    + "needs no application context and the dependency rule holds by construction");

    // ---------------------------------------------------------------- dependency direction

    @ArchTest
    static final ArchRule layersPointInward = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.placefy..")
            .layer("Domain")
            .definedBy(DOMAIN)
            .layer("Application")
            .definedBy(APPLICATION)
            .layer("Infrastructure")
            .definedBy(INFRASTRUCTURE)
            .layer("Web")
            .definedBy(WEB)
            .whereLayer("Web")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Web", "Infrastructure")
            // Web reaches the domain for value objects such as UserId. That is still an inward
            // dependency; what the rule forbids is anything pointing outward.
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Web")
            .because("infrastructure implements ports declared inward; nothing points outward");

    @ArchTest
    static final ArchRule infrastructureIsReachedOnlyThroughPorts = noClasses()
            .that()
            .resideInAPackage(WEB)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(INFRASTRUCTURE)
            .because("the web layer talks to inbound ports; which adapter satisfies them is not its concern");

    // ---------------------------------------------------------------- controllers

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .because("a controller that can reach storage will eventually contain a rule that belongs in a use case");

    @ArchTest
    static final ArchRule outboundPortsAreInterfaces = classes()
            .that()
            .resideInAPackage("com.placefy.application.port.out..")
            .and()
            .areNotRecords()
            .should()
            .beInterfaces()
            .because("a port is a contract the application declares, not an implementation it ships");

    @ArchTest
    static final ArchRule noFieldInjection = fields()
            .should()
            .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("constructor injection makes an unsatisfiable dependency a compile-time problem "
                    + "and lets every class be built in a test without reflection");

    // ---------------------------------------------------------------- no AI/ML (ADR-006)

    /**
     * The current version of APRM uses no AI, ML or LLM anywhere — not in scoring, and not in
     * explanations either, which come from deterministic templates. This covers the whole
     * codebase, not only the decision layer, because an explanation produced by a model is still a
     * claim the product makes to a student. Extend the list when a new client library appears.
     */
    @ArchTest
    static final ArchRule noAiOrMachineLearningLibraries = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    // LLM provider SDKs and orchestration frameworks
                    "com.anthropic..",
                    "com.openai..",
                    "com.theokanning.openai..",
                    "com.azure.ai..",
                    "com.google.cloud.vertexai..",
                    "com.google.genai..",
                    "software.amazon.awssdk.services.bedrock..",
                    "software.amazon.awssdk.services.bedrockruntime..",
                    "dev.langchain4j..",
                    "org.springframework.ai..",
                    "io.github.ollama4j..",
                    // Machine-learning and inference libraries
                    "ai.djl..",
                    "org.tensorflow..",
                    "org.deeplearning4j..",
                    "org.nd4j..",
                    "ai.onnxruntime..",
                    "smile..",
                    "weka..",
                    "org.apache.spark.ml..",
                    "org.apache.spark.mllib..",
                    "hex..",
                    "water..")
            .because("ADR-006: every score, gap, priority, roadmap and explanation is produced by "
                    + "deterministic rules; AI/ML is a documented future enhancement, not a dependency");

    // ---------------------------------------------------------------- determinism

    private static final DescribedPredicate<JavaMethodCall> AMBIENT_NONDETERMINISM = ambientNondeterminism();

    /**
     * The rule this codebase exists to protect.
     *
     * <p>Placefy's scores, gaps and roadmaps must be pure functions of stored inputs: the same
     * assessment must produce the same readiness score in a year's time, and a persisted scoring
     * run must be reproducible from its recorded inputs alone. A single {@code Instant.now()} or
     * {@code UUID.randomUUID()} inside that logic makes it unreproducible, and it would be found
     * months later by a support ticket rather than by a test. Time and identifiers arrive through
     * {@code TimeProvider} and {@code IdGenerator}; randomness has no place inward of
     * infrastructure at all.
     */
    @ArchTest
    static final ArchRule decisionLogicReadsNoClockAndNoRandomSource = noClasses()
            .that()
            .resideInAnyPackage(DOMAIN, APPLICATION)
            .should()
            .callMethodWhere(AMBIENT_NONDETERMINISM)
            .because("every result Placefy computes must be reproducible from its stored inputs; "
                    + "time and ids come through ports, and randomness stays in infrastructure");

    private static DescribedPredicate<JavaMethodCall> ambientNondeterminism() {
        Set<String> randomSources = Set.of("java.util.Random", "java.security.SecureRandom", "java.util.SplittableRandom");

        return new DescribedPredicate<>("a clock read or a random source") {
            @Override
            public boolean test(JavaMethodCall call) {
                String owner = call.getTargetOwner().getFullName();
                String method = call.getTarget().getName();

                if (randomSources.contains(owner)) {
                    return true;
                }
                if ("now".equals(method) && owner.startsWith("java.time.")) {
                    return true;
                }
                if ("java.time.Clock".equals(owner)) {
                    return true;
                }
                if ("java.lang.System".equals(owner)) {
                    return "currentTimeMillis".equals(method) || "nanoTime".equals(method);
                }
                if ("java.lang.Math".equals(owner)) {
                    return "random".equals(method);
                }
                if ("java.util.UUID".equals(owner)) {
                    return "randomUUID".equals(method);
                }
                return false;
            }
        };
    }
}
