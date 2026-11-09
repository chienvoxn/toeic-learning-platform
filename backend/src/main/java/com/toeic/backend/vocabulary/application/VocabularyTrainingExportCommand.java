
package com.toeic.backend.vocabulary.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
public class VocabularyTrainingExportCommand implements ApplicationRunner {

    private final VocabularyTrainingDatasetExporter exporter;

    public VocabularyTrainingExportCommand(
            VocabularyTrainingDatasetExporter exporter
    ) {
        this.exporter = exporter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("export-vocabulary-training")) {
            return;
        }

        List<String> userIds = args.getOptionValues("training-user-id");
        List<String> outputPaths = args.getOptionValues("training-output");

        if (userIds == null || userIds.size() != 1
                || outputPaths == null || outputPaths.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected --training-user-id and --training-output"
            );
        }

        UUID userId = UUID.fromString(userIds.get(0));
        Path output = Path.of(outputPaths.get(0));

        int count = exporter.export(userId, output);

        System.out.println(
                "Vocabulary training examples exported: " + count
        );
    }
}
