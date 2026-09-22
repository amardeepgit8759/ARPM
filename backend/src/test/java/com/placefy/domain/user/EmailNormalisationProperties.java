package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class EmailNormalisationProperties {

    @Property
    void normalisationIsIdempotent(@ForAll("addresses") String raw) {
        Email once = Email.of(raw);
        Email twice = Email.of(once.value());
        assertThat(twice).isEqualTo(once);
    }

    @Property
    void normalisedValueIsAlwaysLowerCase(@ForAll("addresses") String raw) {
        String value = Email.of(raw).value();
        assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT));
    }

    @Property
    void caseAndSurroundingWhitespaceNeverProduceDistinctEmails(@ForAll("addresses") String raw) {
        assertThat(Email.of("  " + raw.toUpperCase(Locale.ROOT) + "  ")).isEqualTo(Email.of(raw));
    }

    @Provide
    Arbitrary<String> addresses() {
        Arbitrary<String> local = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> host = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> tld = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(6);
        return Combinators.combine(local, host, tld).as((l, h, t) -> l + "@" + h + "." + t);
    }
}
