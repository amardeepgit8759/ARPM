package com.placefy.domain.taxonomy;

public record SkillCode(String value) {

    public SkillCode {
        value = Codes.validate("skillCode", value);
    }

    public static SkillCode of(String value) {
        return new SkillCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
