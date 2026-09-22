package com.placefy.domain.taxonomy;

public record DomainCode(String value) {

    public DomainCode {
        value = Codes.validate("domainCode", value);
    }

    public static DomainCode of(String value) {
        return new DomainCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
