
package com.toeic.backend.vocabulary.application;

import java.util.List;

public record VocabularyMlModel(
        String modelType,
        int version,
        List<String> features,
        List<Double> mean,
        List<Double> scale,
        List<Double> coefficients,
        double intercept,
        int trainingExamples,
        int validationExamples
) {
}
