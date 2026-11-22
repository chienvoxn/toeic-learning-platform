package com.toeic.backend.vocabulary.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VocabularyMlInferenceServiceTest {
    @TempDir
    Path directory;

    @Test
    void validModelPredictsAndInvalidOrMissingModelFallsBack() throws Exception {
        var features = new VocabularyLearningFeatures(1, 2, 2.5, 3, 0, 5, 3, 0.2);
        Path model = directory.resolve("model.json");
        Files.writeString(model, """
                {"modelType":"logistic_regression","version":1,
                 "features":["repetitionCount","intervalDays","easeFactor","daysSinceLastReview",
                             "daysUntilNextReview","totalReviews","averageRating","failureRate"],
                 "mean":[0,0,0,0,0,0,0,0],"scale":[1,1,1,1,1,1,1,1],
                 "coefficients":[1,0,0,0,0,0,0,0],"intercept":0,
                 "trainingExamples":10,"validationExamples":5}
                """);
        var inference = new VocabularyMlInferenceService(new ObjectMapper(), model.toString());
        assertTrue(inference.isModelAvailable());
        assertEquals(1.0 / (1.0 + Math.exp(-1)), inference.predictForgetProbability(features).orElseThrow(), 1e-12);

        Files.writeString(model, "{}");
        assertFalse(new VocabularyMlInferenceService(new ObjectMapper(), model.toString()).isModelAvailable());
        assertFalse(new VocabularyMlInferenceService(new ObjectMapper(), directory.resolve("missing.json").toString())
                .predictForgetProbability(features).isPresent());
    }

    @Test
    void generatedModelMatchesPythonProbabilityWhenProvided() {
        String modelPath = System.getProperty("tv3.test.model");
        org.junit.jupiter.api.Assumptions.assumeTrue(modelPath != null);
        var inference = new VocabularyMlInferenceService(new ObjectMapper(), modelPath);
        assertTrue(inference.isModelAvailable());
        var features = new VocabularyLearningFeatures(1, 2, 2.5, 3, 0, 5, 3, 0.2);
        assertEquals(0.42889511502899985, inference.predictForgetProbability(features).orElseThrow(), 1e-12);
    }
}
