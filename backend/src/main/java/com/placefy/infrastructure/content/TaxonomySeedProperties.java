package com.placefy.infrastructure.content;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls the startup seeding of taxonomy content.
 *
 * @param enabled off by default. A deployment that seeds on every boot is convenient locally
 *     and surprising in production, so switching it on is always a deliberate act.
 * @param path the {@code content/taxonomy} directory. Relative paths resolve against the
 *     working directory, which is {@code backend/} under Gradle and {@code /app} in the image.
 * @param publish whether to publish the version after importing it. Importing says the content
 *     is well-formed; publishing says it is what the product should use. Local development
 *     wants both in one step; a real deployment should publish through the admin endpoint that
 *     slice 2C adds, so the decision is recorded against a person.
 */
@ConfigurationProperties(prefix = "placefy.taxonomy.seed")
public record TaxonomySeedProperties(boolean enabled, String path, boolean publish) {

    public TaxonomySeedProperties {
        if (enabled && (path == null || path.isBlank())) {
            throw new IllegalStateException(
                    "placefy.taxonomy.seed.path must be set when taxonomy seeding is enabled.");
        }
    }
}
