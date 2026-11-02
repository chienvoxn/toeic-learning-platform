
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateVocabularyRequest;
import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyService(
            VocabularyRepository vocabularyRepository
    ) {
        this.vocabularyRepository = vocabularyRepository;
    }

    @Transactional
    public VocabularyResponse create(
            UUID currentUserId,
            CreateVocabularyRequest request
    ) {
        Vocabulary vocabulary = new Vocabulary(
                currentUserId,
                request.word().trim(),
                request.meaning().trim()
        );

        vocabulary.setPartOfSpeech(request.partOfSpeech());
        vocabulary.setPronunciation(request.pronunciation());
        vocabulary.setPronunciationAudioResourceId(
                request.pronunciationAudioResourceId()
        );
        vocabulary.setExample(request.example());
        vocabulary.setNote(request.note());

        Vocabulary saved = vocabularyRepository.save(vocabulary);

        return VocabularyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<VocabularyResponse> getAll(
            UUID currentUserId,
            String keyword,
            Pageable pageable
    ) {
        Page<Vocabulary> result;

        if (keyword == null || keyword.isBlank()) {
            result = vocabularyRepository.findByCreatedBy(
                    currentUserId,
                    pageable
            );
        } else {
            result =
                vocabularyRepository
                    .findByCreatedByAndWordContainingIgnoreCase(
                        currentUserId,
                        keyword.trim(),
                        pageable
                    );
        }

        return result.map(VocabularyResponse::from);
    }
}
