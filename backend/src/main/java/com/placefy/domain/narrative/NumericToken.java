package com.placefy.domain.narrative;

import java.math.BigDecimal;

/**
 * One numeric claim found in generated prose.
 *
 * @param text exactly as it appeared, so a rejection message can quote it back
 * @param value the claim itself. Units are stripped: "62%" and "62" both claim 62, because the
 *     percent sign is presentation. A narrative that converts — writing "0.62" where the engine
 *     computed 62 — has performed arithmetic and produces a value that will not match.
 * @param startIndex offset in the masked text, so two identical claims can be told apart
 */
record NumericToken(String text, BigDecimal value, NumericTokenKind kind, int startIndex) {}
