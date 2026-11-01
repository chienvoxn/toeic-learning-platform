
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateVocabularyRequest;
import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private VocabularyService vocabularyService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void create_shouldSaveVocabularySuccessfully() {
        CreateVocabularyRequest request =
                new CreateVocabularyRequest(
                        " accomplish ",
                        " Hoan thanh ",
                        "verb",
                        null,
                        null,
                        null,
                        null
                );

        when(vocabularyRepository.save(any(Vocabulary.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        VocabularyResponse response =
                vocabularyService.create(userId, request);

        assertEquals("accomplish", response.word());
        assertEquals("Hoan thanh", response.meaning());
        assertNotNull(response.vocabularyId());

        verify(vocabularyRepository, times(1))
                .save(any(Vocabulary.class));
    }

    @Test
    void getAll_shouldReturnUserVocabularies() {
        Vocabulary vocabulary =
                new Vocabulary(
                        userId,
                        "achieve",
                        "Dat duoc"
                );

        PageRequest pageable = PageRequest.of(0, 10);

        when(vocabularyRepository.findByCreatedBy(
                userId, pageable
        )).thenReturn(
                new PageImpl<>(List.of(vocabulary))
        );

        Page<VocabularyResponse> result =
                vocabularyService.getAll(
                        userId,
                        null,
                        pageable
                );

        assertEquals(1, result.getTotalElements());
        assertEquals("achieve", result.getContent().get(0).word());

        verify(vocabularyRepository)
                .findByCreatedBy(userId, pageable);
    }

    @Test
    void getAll_shouldSearchByKeyword() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(vocabularyRepository
                .findByCreatedByAndWordContainingIgnoreCase(
                        userId,
                        "achieve",
                        pageable
                )
        ).thenReturn(Page.empty(pageable));

        Page<VocabularyResponse> result =
                vocabularyService.getAll(
                        userId,
                        " achieve ",
                        pageable
                );

        assertTrue(result.isEmpty());

        verify(vocabularyRepository)
                .findByCreatedByAndWordContainingIgnoreCase(
                        userId,
                        "achieve",
                        pageable
                );
    }
}
