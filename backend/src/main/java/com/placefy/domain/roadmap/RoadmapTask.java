package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.Objects;
import java.util.Optional;

/**
 * One block of work on one day.
 *
 * @param sessionIndex which session of this sub-skill's study or revision this is, 1-based, and
 *     {@code sessionCount} how many there are in total. A student seeing "session 2 of 3" knows
 *     the topic continues; without it a split topic looks like three unrelated fragments.
 */
public record RoadmapTask(
        RoadmapTaskType type,
        SubSkillCode subSkillCode,
        String subSkillName,
        int minutes,
        int sessionIndex,
        int sessionCount) {

    public RoadmapTask {
        Objects.requireNonNull(type, "type");
        if (minutes < 1) {
            throw new DomainValidationException("minutes", "A task must occupy at least one minute.");
        }
        if (type == RoadmapTaskType.CHECKPOINT) {
            if (subSkillCode != null) {
                throw new DomainValidationException(
                        "subSkillCode", "A checkpoint covers the plan so far, not one sub-skill.");
            }
        } else if (subSkillCode == null) {
            throw new DomainValidationException("subSkillCode", type + " must name a sub-skill.");
        }
        if (sessionIndex < 1 || sessionIndex > sessionCount) {
            throw new DomainValidationException(
                    "sessionIndex", "Session " + sessionIndex + " of " + sessionCount + " is not a valid position.");
        }
    }

    static RoadmapTask study(SubSkillCode code, String name, int minutes, int index, int count) {
        return new RoadmapTask(RoadmapTaskType.STUDY, code, name, minutes, index, count);
    }

    static RoadmapTask revision(SubSkillCode code, String name, int minutes, int index, int count) {
        return new RoadmapTask(RoadmapTaskType.REVISION, code, name, minutes, index, count);
    }

    static RoadmapTask checkpoint(int minutes) {
        return new RoadmapTask(RoadmapTaskType.CHECKPOINT, null, "Assessment checkpoint", minutes, 1, 1);
    }

    public Optional<SubSkillCode> subSkill() {
        return Optional.ofNullable(subSkillCode);
    }

    public boolean isStudy() {
        return type == RoadmapTaskType.STUDY;
    }
}
