
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VocabularyTrainingDatasetService {

    private final ReviewStateRepository reviewStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final VocabularyRepository vocabularyRepository;
    private final VocabularyTrainingDatasetBuilder datasetBuilder;

    public VocabularyTrainingDatasetService(
            ReviewStateRepository reviewStateRepository,
            ReviewHistoryRepository reviewHistoryRepository,
            VocabularyRepository vocabularyRepository,
            VocabularyTrainingDatasetBuilder datasetBuilder
    ) {
        this.reviewStateRepository = reviewStateRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.datasetBuilder = datasetBuilder;
    }

    @Transactional(readOnly = true)
    public List<VocabularyTrainingExample> buildDataset(
            UUID userId
    ) {
        List<ReviewState> states =
                reviewStateRepository.findByUserId(userId);

        List<VocabularyTrainingExample> dataset =
                new ArrayList<>();

        for (ReviewState state : states) {

            UUID vocabularyId = state.getVocabularyId();

            if (!vocabularyRepository
                    .existsByVocabularyIdAndCreatedBy(
                            vocabularyId,
                            userId
                    )) {
                continue;
            }

            List<ReviewHistory> history =
                    reviewHistoryRepository
                            .findByReviewStateIdOrderByReviewedAtDesc(
                                    state.getReviewStateId()
                            );

            if (history.size() < 2) {
                continue;
            }

            dataset.addAll(
                    datasetBuilder.build(
                            userId,
                            vocabularyId,
                            history
                    )
            );
        }

        return dataset.stream()
                .sorted(
                        java.util.Comparator.comparing(
                                VocabularyTrainingExample::reviewedAt
                        )
                )
                .toList();
    }
}
