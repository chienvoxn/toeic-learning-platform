
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateVocabularyRequest;
import com.toeic.backend.vocabulary.api.UpdateVocabularyRequest;
import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private VocabularyService vocabularyService;

    private UUID userId;
    private UUID otherUserId;
    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        vocabulary = new Vocabulary(
                userId,
                "accomplish",
                "complete"
        );
    }

    @Test
    void create_shouldCreateVocabulary() {
        CreateVocabularyRequest request = new CreateVocabularyRequest(
                " accomplish ",
                " complete ",
                "verb",
                null,
                null,
                "She accomplished her goal.",
                "Test note"
        );

        when(vocabularyRepository.save(any(Vocabulary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VocabularyResponse response = vocabularyService.create(
                userId,
                request
        );

        assertNotNull(response);
        assertEquals("accomplish", response.word());
        assertEquals("complete", response.meaning());
        assertEquals("verb", response.partOfSpeech());

        verify(vocabularyRepository).save(any(Vocabulary.class));
    }

    @Test
    void getAll_shouldReturnOwnedVocabularies() {
        PageRequest pageable = PageRequest.of(0, 20);

        when(vocabularyRepository.findByCreatedBy(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(vocabulary)));

        Page<VocabularyResponse> result = vocabularyService.getAll(
                userId,
                null,
                pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("accomplish", result.getContent().get(0).word());

        verify(vocabularyRepository)
                .findByCreatedBy(userId, pageable);
    }

    @Test
    void getAll_shouldSearchByKeyword() {
        PageRequest pageable = PageRequest.of(0, 20);

        when(vocabularyRepository
                .findByCreatedByAndWordContainingIgnoreCase(
                        userId,
                        "accom",
                        pageable
                ))
                .thenReturn(new PageImpl<>(List.of(vocabulary)));

        Page<VocabularyResponse> result = vocabularyService.getAll(
                userId,
                " accom ",
                pageable
        );

        assertEquals(1, result.getTotalElements());

        verify(vocabularyRepository)
                .findByCreatedByAndWordContainingIgnoreCase(
                        userId,
                        "accom",
                        pageable
                );
    }

    @Test
    void getById_shouldReturnOwnedVocabulary() {
        UUID vocabularyId = vocabulary.getVocabularyId();

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(vocabulary));

        VocabularyResponse response = vocabularyService.getById(
                userId,
                vocabularyId
        );

        assertEquals(vocabularyId, response.vocabularyId());
        assertEquals("accomplish", response.word());
    }

    @Test
    void getById_shouldReturn404WhenNotFound() {
        UUID vocabularyId = UUID.randomUUID();

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vocabularyService.getById(userId, vocabularyId)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void getById_shouldReturn404ForOtherUserVocabulary() {
        Vocabulary otherVocabulary = new Vocabulary(
                otherUserId,
                "private",
                "not owned"
        );

        UUID vocabularyId = otherVocabulary.getVocabularyId();

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(otherVocabulary));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vocabularyService.getById(userId, vocabularyId)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void update_shouldUpdateOwnedVocabulary() {
        UUID vocabularyId = vocabulary.getVocabularyId();

        UpdateVocabularyRequest request = new UpdateVocabularyRequest(
                "achieve",
                "successfully complete",
                "verb",
                null,
                null,
                "She achieved her goal.",
                "Updated note"
        );

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(vocabulary));

        // UPDATE su dung saveAndFlush thay vi save.
        when(vocabularyRepository.saveAndFlush(any(Vocabulary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VocabularyResponse response = vocabularyService.update(
                userId,
                vocabularyId,
                request
        );

        assertNotNull(response);
        assertEquals(vocabularyId, response.vocabularyId());
        assertEquals("achieve", response.word());
        assertEquals("successfully complete", response.meaning());
        assertEquals("verb", response.partOfSpeech());
        assertEquals("Updated note", response.note());

        verify(vocabularyRepository)
                .saveAndFlush(any(Vocabulary.class));
    }

    @Test
    void update_shouldReturn404ForOtherUserVocabulary() {
        Vocabulary otherVocabulary = new Vocabulary(
                otherUserId,
                "private",
                "not owned"
        );

        UUID vocabularyId = otherVocabulary.getVocabularyId();

        UpdateVocabularyRequest request = new UpdateVocabularyRequest(
                "modified",
                "modified meaning",
                null,
                null,
                null,
                null,
                null
        );

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(otherVocabulary));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vocabularyService.update(userId, vocabularyId, request)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(vocabularyRepository, never())
                .saveAndFlush(any(Vocabulary.class));
    }

    @Test
    void delete_shouldDeleteOwnedVocabulary() {
        UUID vocabularyId = vocabulary.getVocabularyId();

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(vocabulary));

        vocabularyService.delete(userId, vocabularyId);

        verify(vocabularyRepository).delete(vocabulary);
    }

    @Test
    void delete_shouldReturn404ForOtherUserVocabulary() {
        Vocabulary otherVocabulary = new Vocabulary(
                otherUserId,
                "private",
                "not owned"
        );

        UUID vocabularyId = otherVocabulary.getVocabularyId();

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(otherVocabulary));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> vocabularyService.delete(userId, vocabularyId)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(vocabularyRepository, never())
                .delete(any(Vocabulary.class));
    }
}
