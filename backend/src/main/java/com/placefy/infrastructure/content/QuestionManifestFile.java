package com.placefy.infrastructure.content;

import java.util.List;

/**
 * Binding for {@code content/questions/questions.yaml}.
 *
 * @param taxonomyVersion the vocabulary these questions were written against. Tags are checked
 *     against exactly this version, because a tag that resolved last month may name a sub-skill
 *     a newer taxonomy renamed or removed.
 * @param banks file names under {@code banks/}, without the extension
 */
record QuestionManifestFile(String bankVersion, String taxonomyVersion, String description, List<String> banks) {}
