package com.placefy.domain.taxonomy;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * A digest of the content files a taxonomy version was built from.
 *
 * <p>Exists to catch the one mistake version labels cannot prevent on their own: editing
 * content without bumping the label. Re-importing the same label with a different checksum is
 * rejected, so a version always means exactly the bytes it meant the first time.
 */
public record ContentChecksum(String value) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public ContentChecksum {
        if (value == null || !HEX_64.matcher(value).matches()) {
            throw new DomainValidationException(
                    "sourceChecksum", "Content checksum must be 64 lowercase hex characters.");
        }
    }

    public static ContentChecksum of(String value) {
        return new ContentChecksum(value);
    }

    @Override
    public String toString() {
        return value.substring(0, 12) + "...";
    }
}
