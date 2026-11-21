package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewState;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

final class LearningRoundProgress {
    private final Set<UUID> reviewed;
    private final Set<UUID> completed;
    private final Set<UUID> queue;
    private final Map<UUID, OffsetDateTime> deferredUntil = new HashMap<>();
    private long roundNumber;
    private long attempts;

    LearningRoundProgress(JsonNode stored, List<UUID> deckCards, Function<UUID, Optional<ReviewState>> legacyState) {
        Map<String, UUID> validCards = new HashMap<>();
        deckCards.forEach(id -> validCards.put(id.toString(), id));

        reviewed = readIds(stored.path("reviewedIds"), validCards);
        boolean legacy = !stored.path("completedIds").isArray();
        completed = readIds(stored.path(legacy ? "easyIds" : "completedIds"), validCards);
        queue = readIds(stored.path("roundIds"), validCards);
        queue.addAll(deckCards);

        for (UUID id : deckCards) {
            String savedDate = stored.path("deferredUntil").path(id.toString()).asString("");
            if (!savedDate.isEmpty()) {
                try {
                    deferredUntil.put(id, OffsetDateTime.parse(savedDate));
                } catch (DateTimeParseException ignored) {
                    // Old snapshots may contain a malformed date.
                }
            }
            if (legacy && reviewed.contains(id)) {
                legacyState.apply(id).ifPresent(state -> {
                    if (state.getIntervalDays() >= 1) {
                        completed.add(id);
                    } else if (state.getNextReviewAt() != null) {
                        deferredUntil.put(id, state.getNextReviewAt());
                    }
                });
            }
        }
        queue.removeAll(completed);
        deferredUntil.keySet().retainAll(queue);
        roundNumber = Math.max(1, stored.path("roundNumber").asInt(1));
        attempts = Math.max(0, stored.path("attempts").asInt(0));
    }

    boolean canRate(UUID vocabularyId, OffsetDateTime now) {
        OffsetDateTime dueAt = deferredUntil.get(vocabularyId);
        return queue.contains(vocabularyId) && (dueAt == null || !now.isBefore(dueAt));
    }

    void apply(UUID vocabularyId, ReviewState review) {
        reviewed.add(vocabularyId);
        queue.remove(vocabularyId);
        if (review.getIntervalDays() >= 1) {
            completed.add(vocabularyId);
            deferredUntil.remove(vocabularyId);
        } else {
            deferredUntil.put(vocabularyId, review.getNextReviewAt());
            queue.add(vocabularyId);
            if (queue.size() == 1) {
                roundNumber++;
            }
        }
        attempts++;
    }

    SnapshotData snapshot() {
        Map<UUID, String> delays = new HashMap<>();
        deferredUntil.forEach((id, date) -> delays.put(id, date.toString()));
        return new SnapshotData(reviewed, completed, delays, queue, roundNumber, attempts,
                queue.stream().findFirst().orElse(null));
    }

    private static Set<UUID> readIds(JsonNode values, Map<String, UUID> validCards) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                UUID id = validCards.get(value.asString(""));
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    record SnapshotData(
            Set<UUID> reviewedIds, Set<UUID> completedIds, Map<UUID, String> deferredUntil, Set<UUID> roundIds,
            long roundNumber, long attempts, UUID currentId
    ) {}
}
