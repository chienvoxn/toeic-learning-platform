
package com.toeic.backend.vocabulary.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class VocabularyMlInferenceService {

    private static final List<String> EXPECTED_FEATURES = List.of(
            "repetitionCount",
            "intervalDays",
            "easeFactor",
            "daysSinceLastReview",
            "daysUntilNextReview",
            "totalReviews",
            "averageRating",
            "failureRate"
    );

    private final Optional<VocabularyMlModel> model;

    public VocabularyMlInferenceService(
            ObjectMapper objectMapper,
            @Value("${vocabulary.ml.model-path:ml/vocabulary/vocabulary_model.json}")
            String modelPath
    ) {
        this.model = loadModel(
                objectMapper,
                Path.of(modelPath)
        );
    }

    public boolean isModelAvailable() {
        return model.isPresent();
    }

    public Optional<Double> predictForgetProbability(
            VocabularyLearningFeatures features
    ) {
        if (model.isEmpty() || features == null) {
            return Optional.empty();
        }

        VocabularyMlModel loaded = model.get();

        double[] values = {
                features.repetitionCount(),
                features.intervalDays(),
                features.easeFactor(),
                features.daysSinceLastReview(),
                features.daysUntilNextReview(),
                features.totalReviews(),
                features.averageRating(),
                features.failureRate()
        };

        double logit = loaded.intercept();

        for (int i = 0; i < values.length; i++) {
            double normalized =
                    (values[i] - loaded.mean().get(i))
                            / loaded.scale().get(i);

            logit += normalized * loaded.coefficients().get(i);
        }

        if (!Double.isFinite(logit)) {
            return Optional.empty();
        }

        double probability;

        if (logit >= 0) {
            probability =
                    1.0 / (1.0 + Math.exp(-logit));
        } else {
            double exp = Math.exp(logit);
            probability = exp / (1.0 + exp);
        }

        return Optional.of(probability);
    }

    private Optional<VocabularyMlModel> loadModel(
            ObjectMapper objectMapper,
            Path path
    ) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            VocabularyMlModel loaded =
                    objectMapper.readValue(
                            path.toFile(),
                            VocabularyMlModel.class
                    );

            if (!isValid(loaded)) {
                return Optional.empty();
            }

            return Optional.of(loaded);

        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean isValid(VocabularyMlModel loaded) {
        if (loaded == null
                || !"logistic_regression".equals(loaded.modelType())
                || loaded.version() != 1
                || !EXPECTED_FEATURES.equals(loaded.features())
                || loaded.mean() == null
                || loaded.scale() == null
                || loaded.coefficients() == null
                || loaded.trainingExamples() <= 0
                || loaded.validationExamples() <= 0
                || !Double.isFinite(loaded.intercept())) {
            return false;
        }

        int size = EXPECTED_FEATURES.size();

        if (loaded.mean().size() != size
                || loaded.scale().size() != size
                || loaded.coefficients().size() != size) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            Double mean = loaded.mean().get(i);
            Double scale = loaded.scale().get(i);
            Double coefficient = loaded.coefficients().get(i);

            if (mean == null
                    || scale == null
                    || coefficient == null
                    || !Double.isFinite(mean)
                    || !Double.isFinite(scale)
                    || !Double.isFinite(coefficient)
                    || scale <= 0) {
                return false;
            }
        }

        return true;
    }
}
