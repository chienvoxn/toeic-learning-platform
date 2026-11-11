
package com.toeic.backend.vocabulary.application;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
public class VocabularyTrainingDatasetExporter {

    private final VocabularyTrainingDatasetService datasetService;
    private final ObjectMapper objectMapper;

    public VocabularyTrainingDatasetExporter(
            VocabularyTrainingDatasetService datasetService,
            ObjectMapper objectMapper
    ) {
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    public int export(UUID userId, Path outputPath) throws IOException {
        if (userId == null || outputPath == null) {
            throw new IllegalArgumentException(
                    "User ID and output path must not be null"
            );
        }

        List<VocabularyTrainingExample> examples =
                datasetService.buildDataset(userId);

        Path absolutePath = outputPath.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(absolutePath.toFile(), examples);

        return examples.size();
    }
}
