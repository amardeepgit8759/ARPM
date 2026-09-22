package com.placefy.domain.taxonomy;

/**
 * Identifies a sub-skill uniquely across the whole taxonomy, not merely within its skill.
 *
 * <p>Flat and global rather than path-qualified ({@code dsa.graphs.traversal}) on purpose.
 * Prerequisites cross skill and domain boundaries, and a path-qualified code would change
 * whenever a sub-skill were reorganised into a different skill — silently orphaning every
 * prerequisite reference and every persisted score that pointed at it.
 */
public record SubSkillCode(String value) {

    public SubSkillCode {
        value = Codes.validate("subSkillCode", value);
    }

    public static SubSkillCode of(String value) {
        return new SubSkillCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
