package com.placefy.application.port.in;

import com.placefy.domain.taxonomy.TaxonomyVersionLabel;

/**
 * Moves a DRAFT version into circulation.
 *
 * <p>Separated from import because the two are different decisions: importing says "this
 * content is well-formed", publishing says "this content is what the product should use". In
 * slice 2C this gains an admin-only endpoint; nothing else may call it.
 */
public interface PublishTaxonomyVersion {

    TaxonomyVersionSummary handle(PublishTaxonomyVersionCommand command);

    record PublishTaxonomyVersionCommand(TaxonomyVersionLabel label) {}
}
