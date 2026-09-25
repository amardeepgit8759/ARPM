package com.placefy.infrastructure.content;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Binding for one file under {@code content/questions/banks/}.
 *
 * <p>Boxed types throughout, so an omitted field arrives as null and is reported as missing
 * rather than binding to a zero that gets rejected later with a message about the number zero.
 */
record QuestionBankFile(List<QuestionFile> questions) {

    record QuestionFile(
            String code,
            String type,
            Integer difficulty,
            String stem,
            List<OptionFile> options,
            List<String> correctOptions,
            String explanation,
            List<TagFile> tags,
            OriginalityFile originality,
            ReviewFile review) {}

    record OptionFile(String id, String text) {}

    record TagFile(String subSkill, BigDecimal weight) {}

    record OriginalityFile(String attestedBy, LocalDate attestedOn, Boolean originalWork, String sourceNote) {}

    record ReviewFile(String reviewedBy, LocalDate reviewedOn, String outcome, String comment) {}
}
