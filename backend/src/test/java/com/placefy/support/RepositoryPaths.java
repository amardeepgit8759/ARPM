package com.placefy.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates files that live outside {@code backend/}, such as {@code content/}.
 *
 * <p>Walks up from the working directory rather than hard-coding {@code ../content}, because the
 * working directory differs between a Gradle run, an IDE run and a run from the repository root,
 * and a test that only passes under one of them is a test people learn to distrust.
 */
public final class RepositoryPaths {

    private RepositoryPaths() {}

    public static Path resolve(String relativeToRepositoryRoot) {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            Path candidate = current.resolve(relativeToRepositoryRoot);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        throw new IllegalStateException(
                "Could not find '" + relativeToRepositoryRoot + "' walking up from "
                        + Path.of("").toAbsolutePath());
    }

    /** The real, shipped taxonomy content. */
    public static Path taxonomyContent() {
        return resolve("content/taxonomy");
    }
}
