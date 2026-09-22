package com.placefy.infrastructure.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.ContentChecksum;
import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.DomainCode;
import com.placefy.domain.taxonomy.EffortHours;
import com.placefy.domain.taxonomy.InvalidTaxonomyException;
import com.placefy.domain.taxonomy.NodeName;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SkillCode;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import com.placefy.domain.taxonomy.TaxonomyViolation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads a taxonomy from YAML on disk and hands back a validated {@link TaxonomyVersion}.
 *
 * <p>This is the only place that knows the content is YAML or that it is on a filesystem. The
 * use cases receive a built aggregate, so nothing above this class ever handles half-parsed
 * content.
 *
 * <p>Errors are collected, not thrown on first sight. Content authoring is an edit-run-edit
 * loop; failing on the first bad line turns a file with six problems into six round trips.
 */
public final class TaxonomyContentLoader {

    public static final String MANIFEST_FILE_NAME = "taxonomy.yaml";
    public static final String DOMAINS_DIRECTORY = "domains";

    private static final String YAML_SUFFIX = ".yaml";

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // A key nobody reads is almost always a typo for one that matters. Failing on it is
            // the difference between "prerequisits: [x]" being an error and being ignored.
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    /**
     * @param contentRoot the {@code content/taxonomy} directory
     * @throws InvalidTaxonomyException listing every problem found, whether it came from parsing
     *     or from the domain's own structural rules
     */
    public TaxonomyVersion load(Path contentRoot, TaxonomyVersionId id, java.time.Instant importedAt) {
        List<TaxonomyViolation> violations = new ArrayList<>();

        Path manifestPath = contentRoot.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            throw new InvalidTaxonomyException(List.of(TaxonomyViolation.malformed(
                    MANIFEST_FILE_NAME, "Manifest not found at " + manifestPath.toAbsolutePath() + ".")));
        }

        TaxonomyManifestFile manifest = read(manifestPath, TaxonomyManifestFile.class, violations);
        if (manifest == null) {
            throw new InvalidTaxonomyException(violations);
        }

        TaxonomyVersionLabel label = readLabel(manifest, violations);
        List<String> declared = manifest.domains() == null ? List.of() : manifest.domains();
        if (declared.isEmpty()) {
            violations.add(TaxonomyViolation.malformed(
                    MANIFEST_FILE_NAME, "The manifest declares no domains. List at least one domain code."));
        }

        Path domainsDirectory = contentRoot.resolve(DOMAINS_DIRECTORY);
        reportUndeclaredFiles(domainsDirectory, declared, violations);

        List<Domain> domains = new ArrayList<>();
        for (String declaredCode : declared) {
            Domain domain = readDomain(domainsDirectory, declaredCode, violations);
            if (domain != null) {
                domains.add(domain);
            }
        }

        // Structural checks need a complete tree. Reporting "unknown prerequisite" for every
        // reference into a file that simply failed to parse would bury the real cause.
        if (!violations.isEmpty()) {
            throw new InvalidTaxonomyException(violations);
        }

        return TaxonomyVersion.importedFrom(
                id, label, checksumOf(contentRoot, manifestPath, domainsDirectory, declared), importedAt, domains);
    }

    private TaxonomyVersionLabel readLabel(TaxonomyManifestFile manifest, List<TaxonomyViolation> violations) {
        try {
            return TaxonomyVersionLabel.of(manifest.taxonomyVersion());
        } catch (DomainValidationException e) {
            violations.add(TaxonomyViolation.malformed(MANIFEST_FILE_NAME, e.getMessage()));
            // A placeholder so loading continues and the author sees every other problem too;
            // the collected violations guarantee this value is never returned to a caller.
            return TaxonomyVersionLabel.of("unreadable");
        }
    }

    private void reportUndeclaredFiles(
            Path domainsDirectory, List<String> declared, List<TaxonomyViolation> violations) {

        if (!Files.isDirectory(domainsDirectory)) {
            violations.add(TaxonomyViolation.malformed(
                    DOMAINS_DIRECTORY, "Domain directory not found at " + domainsDirectory.toAbsolutePath() + "."));
            return;
        }

        Set<String> declaredFileNames = new LinkedHashSet<>();
        declared.forEach(code -> declaredFileNames.add(code + YAML_SUFFIX));

        try (Stream<Path> files = Files.list(domainsDirectory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(YAML_SUFFIX))
                    // Files starting with an underscore are templates for authors, not content.
                    .filter(name -> !name.startsWith("_"))
                    .filter(name -> !declaredFileNames.contains(name))
                    .sorted()
                    .forEach(name -> violations.add(TaxonomyViolation.malformed(
                            DOMAINS_DIRECTORY + "/" + name,
                            "This file is not listed in " + MANIFEST_FILE_NAME
                                    + ", so its content would be silently left out of the taxonomy. Add its "
                                    + "code to the manifest, or remove the file.")));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + domainsDirectory, e);
        }
    }

    private Domain readDomain(Path domainsDirectory, String declaredCode, List<TaxonomyViolation> violations) {
        String fileName = declaredCode + YAML_SUFFIX;
        Path path = domainsDirectory.resolve(fileName);

        if (!Files.isRegularFile(path)) {
            violations.add(TaxonomyViolation.malformed(
                    DOMAINS_DIRECTORY + "/" + fileName,
                    "Declared in " + MANIFEST_FILE_NAME + " but no such file exists."));
            return null;
        }

        TaxonomyDomainFile file = read(path, TaxonomyDomainFile.class, violations);
        if (file == null) {
            return null;
        }

        if (!declaredCode.equals(file.code())) {
            violations.add(TaxonomyViolation.malformed(
                    DOMAINS_DIRECTORY + "/" + fileName,
                    "File declares code '" + file.code() + "' but is named for '" + declaredCode
                            + "'. The file name and the code must agree."));
            return null;
        }

        return buildDomain(fileName, file, violations);
    }

    private Domain buildDomain(String fileName, TaxonomyDomainFile file, List<TaxonomyViolation> violations) {
        int before = violations.size();

        DomainCode code = tryBuild(fileName, () -> DomainCode.of(file.code()), violations);
        NodeName name = tryBuild(fileName, () -> NodeName.of(file.name()), violations);

        List<Skill> skills = new ArrayList<>();
        List<TaxonomyDomainFile.SkillFile> skillFiles = file.skills() == null ? List.of() : file.skills();
        if (skillFiles.isEmpty()) {
            violations.add(TaxonomyViolation.malformed(fileName, "Domain declares no skills."));
        }

        for (TaxonomyDomainFile.SkillFile skillFile : skillFiles) {
            String at = fileName + " / " + skillFile.code();
            SkillCode skillCode = tryBuild(at, () -> SkillCode.of(skillFile.code()), violations);
            NodeName skillName = tryBuild(at, () -> NodeName.of(skillFile.name()), violations);

            List<SubSkill> subSkills = new ArrayList<>();
            List<TaxonomyDomainFile.SubSkillFile> subSkillFiles =
                    skillFile.subSkills() == null ? List.of() : skillFile.subSkills();
            if (subSkillFiles.isEmpty()) {
                violations.add(TaxonomyViolation.malformed(at, "Skill declares no sub_skills."));
            }

            for (TaxonomyDomainFile.SubSkillFile subSkillFile : subSkillFiles) {
                SubSkill subSkill = buildSubSkill(at, subSkillFile, violations);
                if (subSkill != null) {
                    subSkills.add(subSkill);
                }
            }

            if (skillCode != null && skillName != null) {
                skills.add(Skill.of(skillCode, skillName, subSkills));
            }
        }

        boolean clean = violations.size() == before;
        return clean && code != null && name != null ? Domain.of(code, name, skills) : null;
    }

    private SubSkill buildSubSkill(
            String skillLocation, TaxonomyDomainFile.SubSkillFile file, List<TaxonomyViolation> violations) {

        String at = skillLocation + " / " + file.code();

        SubSkillCode code = tryBuild(at, () -> SubSkillCode.of(file.code()), violations);
        NodeName name = tryBuild(at, () -> NodeName.of(file.name()), violations);

        EffortHours effort = null;
        if (file.defaultEffortHours() == null) {
            violations.add(TaxonomyViolation.malformed(at, "default_effort_hours is required."));
        } else {
            effort = tryBuild(at, () -> EffortHours.of(file.defaultEffortHours()), violations);
        }

        List<SubSkillCode> prerequisites = new ArrayList<>();
        for (String prerequisite : file.prerequisites() == null ? List.<String>of() : file.prerequisites()) {
            SubSkillCode parsed = tryBuild(at, () -> SubSkillCode.of(prerequisite), violations);
            if (parsed != null) {
                prerequisites.add(parsed);
            }
        }

        return code != null && name != null && effort != null
                ? SubSkill.of(code, name, effort, prerequisites)
                : null;
    }

    private <T> T tryBuild(String location, java.util.function.Supplier<T> build, List<TaxonomyViolation> out) {
        try {
            return build.get();
        } catch (DomainValidationException e) {
            out.add(TaxonomyViolation.malformed(location, e.getMessage()));
            return null;
        }
    }

    private <T> T read(Path path, Class<T> type, List<TaxonomyViolation> violations) {
        try {
            return yaml.readValue(normalise(Files.readAllBytes(path)), type);
        } catch (IOException e) {
            violations.add(TaxonomyViolation.malformed(
                    path.getFileName().toString(), "Could not be read as YAML: " + rootMessage(e)));
            return null;
        }
    }

    /**
     * Hashes the exact bytes the taxonomy was built from, with line endings normalised.
     *
     * <p>Normalisation matters: git checks the same file out as LF on Linux and frequently CRLF
     * on Windows, so hashing raw bytes would make a version's checksum depend on which machine
     * imported it — and the "content changed without a version bump" check would fire on every
     * cross-platform import.
     */
    private ContentChecksum checksumOf(
            Path contentRoot, Path manifestPath, Path domainsDirectory, List<String> declared) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digestFile(digest, contentRoot, manifestPath);

            // Sorted, not manifest order: reordering the domain list in the manifest changes the
            // taxonomy's meaning and is caught elsewhere, but it must not change this digest for
            // otherwise identical content.
            declared.stream()
                    .map(code -> domainsDirectory.resolve(code + YAML_SUFFIX))
                    .sorted()
                    .forEach(path -> digestFile(digest, contentRoot, path));

            return ContentChecksum.of(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private void digestFile(MessageDigest digest, Path contentRoot, Path path) {
        try {
            // The relative path is hashed too, so renaming a file changes the checksum even when
            // its contents are untouched.
            digest.update(contentRoot.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(normalise(Files.readAllBytes(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    private static byte[] normalise(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Jackson appends the whole source location and a full type chain to its messages, which is
     * noise to someone editing YAML. The first line carries the actual complaint.
     */
    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getSimpleName();
        }
        int sourceMarker = message.indexOf(" at [Source:");
        return (sourceMarker > 0 ? message.substring(0, sourceMarker) : message).trim();
    }
}
