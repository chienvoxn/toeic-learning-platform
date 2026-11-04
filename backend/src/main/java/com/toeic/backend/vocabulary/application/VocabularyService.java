
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateVocabularyRequest;
import com.toeic.backend.vocabulary.api.UpdateVocabularyRequest;
import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyService(
            VocabularyRepository vocabularyRepository
    ) {
        this.vocabularyRepository = vocabularyRepository;
    }

    // 1. CREATE
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

    // 2. READ ALL + SEARCH
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
            result = vocabularyRepository
                    .findByCreatedByAndWordContainingIgnoreCase(
                            currentUserId,
                            keyword.trim(),
                            pageable
                    );
        }

        return result.map(VocabularyResponse::from);
    }

    // 3. READ BY ID
    @Transactional(readOnly = true)
    public VocabularyResponse getById(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        return VocabularyResponse.from(
                findOwnedVocabulary(currentUserId, vocabularyId)
        );
    }

    // 4. UPDATE
    @Transactional
    public VocabularyResponse update(
            UUID currentUserId,
            UUID vocabularyId,
            UpdateVocabularyRequest request
    ) {
        Vocabulary vocabulary = findOwnedVocabulary(
                currentUserId,
                vocabularyId
        );

        vocabulary.setWord(request.word().trim());
        vocabulary.setMeaning(request.meaning().trim());
        vocabulary.setPartOfSpeech(request.partOfSpeech());
        vocabulary.setPronunciation(request.pronunciation());
        vocabulary.setPronunciationAudioResourceId(
                request.pronunciationAudioResourceId()
        );
        vocabulary.setExample(request.example());
        vocabulary.setNote(request.note());

        // Flush de @PreUpdate cap nhat updatedAt
        // truoc khi tao response.
        Vocabulary saved = vocabularyRepository.saveAndFlush(vocabulary);

        return VocabularyResponse.from(saved);
    }

    // 5. DELETE
    @Transactional
    public void delete(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        Vocabulary vocabulary = findOwnedVocabulary(
                currentUserId,
                vocabularyId
        );

        vocabularyRepository.delete(vocabulary);
    }

    // 6. OWNERSHIP CHECK
    private Vocabulary findOwnedVocabulary(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        return vocabularyRepository
                .findById(vocabularyId)
                .filter(v -> v.getCreatedBy().equals(currentUserId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));
    }
}
