package com.placefy.domain.taxonomy;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One reason a taxonomy is invalid, with enough location to find it in the content.
 *
 * @param location where the problem is, in content terms — a file name, a code, or a path such
 *     as {@code dsa / graphs / graph-traversal}. Never a Java class or line number: the person
 *     fixing this is editing YAML.
 */
public record TaxonomyViolation(TaxonomyViolationType type, String location, String message) {

    public TaxonomyViolation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    static TaxonomyViolation emptyTaxonomy() {
        return new TaxonomyViolation(
                TaxonomyViolationType.EMPTY_TAXONOMY, "taxonomy", "A taxonomy must declare at least one domain.");
    }

    static TaxonomyViolation emptyDomain(DomainCode domain) {
        return new TaxonomyViolation(
                TaxonomyViolationType.EMPTY_DOMAIN,
                domain.value(),
                "Domain '" + domain + "' declares no skills.");
    }

    static TaxonomyViolation emptySkill(DomainCode domain, SkillCode skill) {
        return new TaxonomyViolation(
                TaxonomyViolationType.EMPTY_SKILL,
                domain + " / " + skill,
                "Skill '" + skill + "' declares no sub-skills.");
    }

    static TaxonomyViolation duplicateDomainCode(DomainCode code) {
        return new TaxonomyViolation(
                TaxonomyViolationType.DUPLICATE_DOMAIN_CODE,
                code.value(),
                "Domain code '" + code + "' is declared more than once.");
    }

    static TaxonomyViolation duplicateSkillCode(SkillCode code) {
        return new TaxonomyViolation(
                TaxonomyViolationType.DUPLICATE_SKILL_CODE,
                code.value(),
                "Skill code '" + code + "' is declared more than once. Skill codes are unique across the "
                        + "whole taxonomy, not just within a domain.");
    }

    static TaxonomyViolation duplicateSubSkillCode(SubSkillCode code) {
        return new TaxonomyViolation(
                TaxonomyViolationType.DUPLICATE_SUB_SKILL_CODE,
                code.value(),
                "Sub-skill code '" + code + "' is declared more than once. Sub-skill codes are unique across "
                        + "the whole taxonomy, because prerequisites reference them globally.");
    }

    static TaxonomyViolation unknownPrerequisite(SubSkillCode subSkill, SubSkillCode missing) {
        return new TaxonomyViolation(
                TaxonomyViolationType.UNKNOWN_PREREQUISITE,
                subSkill.value(),
                "Sub-skill '" + subSkill + "' requires '" + missing + "', which is not declared anywhere "
                        + "in this taxonomy.");
    }

    static TaxonomyViolation selfPrerequisite(SubSkillCode subSkill) {
        return new TaxonomyViolation(
                TaxonomyViolationType.SELF_PREREQUISITE,
                subSkill.value(),
                "Sub-skill '" + subSkill + "' lists itself as its own prerequisite.");
    }

    static TaxonomyViolation duplicatePrerequisite(SubSkillCode subSkill, SubSkillCode duplicate) {
        return new TaxonomyViolation(
                TaxonomyViolationType.DUPLICATE_PREREQUISITE,
                subSkill.value(),
                "Sub-skill '" + subSkill + "' lists '" + duplicate + "' as a prerequisite more than once.");
    }

    static TaxonomyViolation prerequisiteCycle(List<SubSkillCode> cycle) {
        String path = cycle.stream().map(SubSkillCode::value).collect(Collectors.joining(" -> "));
        return new TaxonomyViolation(
                TaxonomyViolationType.PREREQUISITE_CYCLE,
                path,
                "Prerequisite cycle: " + path + ". No study order can satisfy this.");
    }

    public static TaxonomyViolation malformed(String location, String message) {
        return new TaxonomyViolation(TaxonomyViolationType.MALFORMED_CONTENT, location, message);
    }

    @Override
    public String toString() {
        return "[" + type + "] " + location + ": " + message;
    }
}
