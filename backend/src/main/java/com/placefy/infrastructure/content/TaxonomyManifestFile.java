package com.placefy.infrastructure.content;

import java.util.List;

/**
 * Binding for {@code content/taxonomy/taxonomy.yaml}.
 *
 * <p>The manifest exists so the version label lives in exactly one place and so the set of
 * domains is declared rather than inferred from whatever files happen to be in the directory.
 * A file present but undeclared, or declared but absent, is then a detectable mistake instead
 * of a silent omission from the published vocabulary.
 */
record TaxonomyManifestFile(String taxonomyVersion, String description, List<String> domains) {}
