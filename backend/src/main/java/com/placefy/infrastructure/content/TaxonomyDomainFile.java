package com.placefy.infrastructure.content;

import java.util.List;

/**
 * Binding for one file under {@code content/taxonomy/domains/}.
 *
 * <p>{@code Integer} rather than {@code int} for the effort so an omitted value arrives as null
 * and is reported as missing, instead of silently binding to zero and being rejected later with
 * a message about the number zero that never mentions the absent line.
 */
record TaxonomyDomainFile(String code, String name, String description, List<SkillFile> skills) {

    record SkillFile(String code, String name, List<SubSkillFile> subSkills) {}

    record SubSkillFile(String code, String name, Integer defaultEffortHours, List<String> prerequisites) {}
}
