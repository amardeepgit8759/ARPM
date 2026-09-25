package com.placefy.domain.question;

import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the bank is thin, by sub-skill and difficulty.
 *
 * <p>Driven by the taxonomy rather than by the bank, so a sub-skill with no questions at all
 * appears as a row of zeros instead of being absent. That distinction is the whole value of the
 * report: an assessment cannot measure a sub-skill it has no questions for, and without this the
 * only symptom is a readiness score quietly built on a narrower base than it appears to cover.
 *
 * <p>Pure: it counts what is there and applies no threshold for what "enough" means. How many
 * questions a sub-skill needs before it can be scored is a scoring decision, not a counting one.
 */
public record BankCoverageReport(String bankVersion, String taxonomyVersion, List<SubSkillCoverage> rows) {

    /**
     * @param countsByDifficulty every level 1–5 is present, zero included, so the shape of the
     *     report does not change with the data in it
     */
    public record SubSkillCoverage(
            SubSkillCode code, String name, Map<Difficulty, Integer> countsByDifficulty, int total) {

        public SubSkillCoverage {
            countsByDifficulty = Map.copyOf(countsByDifficulty);
        }

        public boolean isUncovered() {
            return total == 0;
        }

        public int countAt(Difficulty difficulty) {
            return countsByDifficulty.getOrDefault(difficulty, 0);
        }

        /** Levels with no questions at all, ascending. */
        public List<Difficulty> emptyDifficulties() {
            return Difficulty.all().stream().filter(level -> countAt(level) == 0).toList();
        }
    }

    public BankCoverageReport {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static BankCoverageReport of(QuestionBank bank, TaxonomyVersion taxonomy) {
        List<SubSkillCoverage> rows = new ArrayList<>();

        for (SubSkill subSkill : taxonomy.allSubSkills()) {
            Map<Difficulty, Integer> counts = new LinkedHashMap<>();
            Difficulty.all().forEach(level -> counts.put(level, 0));

            int total = 0;
            for (Question question : bank.taggedWith(subSkill.code())) {
                counts.merge(question.difficulty(), 1, Integer::sum);
                total++;
            }

            rows.add(new SubSkillCoverage(subSkill.code(), subSkill.name().value(), counts, total));
        }

        return new BankCoverageReport(bank.bankVersion(), bank.taxonomyVersion(), List.copyOf(rows));
    }

    /** Sub-skills no question touches. The list an author works from. */
    public List<SubSkillCoverage> uncovered() {
        return rows.stream().filter(SubSkillCoverage::isUncovered).toList();
    }

    public List<SubSkillCoverage> covered() {
        return rows.stream().filter(row -> !row.isUncovered()).toList();
    }

    public Optional<SubSkillCoverage> forSubSkill(SubSkillCode code) {
        return rows.stream().filter(row -> row.code().equals(code)).findFirst();
    }

    public int totalSubSkills() {
        return rows.size();
    }

    /**
     * Counts tag attachments, not questions. A question tagged with three sub-skills contributes
     * to three rows, so this exceeds the bank size whenever questions carry more than one tag.
     */
    public int totalTagged() {
        return rows.stream().mapToInt(SubSkillCoverage::total).sum();
    }

    public int countAt(Difficulty difficulty) {
        return rows.stream().mapToInt(row -> row.countAt(difficulty)).sum();
    }
}
