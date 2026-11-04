
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.DeckVocabulary;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.domain.VocabularyDeck;
import com.toeic.backend.vocabulary.infrastructure.DeckVocabularyRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckFlashcardServiceTest {

    @Mock
    private VocabularyDeckRepository deckRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private DeckVocabularyRepository deckVocabularyRepository;

    @InjectMocks
    private DeckFlashcardService flashcardService;

    private UUID userId;
    private UUID deckId;
    private UUID vocabularyId;
    private Vocabulary vocabulary;
    private VocabularyDeck deck;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        deck = new VocabularyDeck(
                userId,
                "TOEIC Business",
                "Business vocabulary"
        );

        deckId = deck.getDeckId();

        vocabulary = new Vocabulary(
                userId,
                "accomplish",
                "complete"
        );

        vocabularyId = vocabulary.getVocabularyId();
    }

    @Test
    void addVocabulary_shouldAddSuccessfully() {
        mockOwnedDeck();
        mockOwnedVocabulary();

        when(deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(deckId, vocabularyId))
                .thenReturn(false);

        flashcardService.addVocabulary(
                userId,
                deckId,
                vocabularyId
        );

        verify(deckVocabularyRepository)
                .save(any(DeckVocabulary.class));
    }

    @Test
    void addVocabulary_shouldReturn409WhenDuplicate() {
        mockOwnedDeck();
        mockOwnedVocabulary();

        when(deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(deckId, vocabularyId))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.addVocabulary(
                        userId, deckId, vocabularyId
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(deckVocabularyRepository, never())
                .save(any(DeckVocabulary.class));
    }

    @Test
    void addVocabulary_shouldReturn404WhenDeckNotOwned() {
        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.addVocabulary(
                        userId, deckId, vocabularyId
                )
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(
                vocabularyRepository,
                deckVocabularyRepository
        );
    }

    @Test
    void addVocabulary_shouldReturn404WhenVocabularyNotOwned() {
        mockOwnedDeck();

        Vocabulary otherVocabulary = new Vocabulary(
                UUID.randomUUID(),
                "private",
                "not owned"
        );

        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(otherVocabulary));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.addVocabulary(
                        userId, deckId, vocabularyId
                )
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(deckVocabularyRepository, never())
                .save(any(DeckVocabulary.class));
    }

    @Test
    void removeVocabulary_shouldRemoveSuccessfully() {
        mockOwnedDeck();

        when(deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(deckId, vocabularyId))
                .thenReturn(true);

        flashcardService.removeVocabulary(
                userId,
                deckId,
                vocabularyId
        );

        verify(deckVocabularyRepository)
                .deleteByIdDeckIdAndIdVocabularyId(
                        deckId,
                        vocabularyId
                );

        verifyNoInteractions(vocabularyRepository);
    }

    @Test
    void removeVocabulary_shouldReturn404WhenLinkMissing() {
        mockOwnedDeck();

        when(deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(deckId, vocabularyId))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.removeVocabulary(
                        userId, deckId, vocabularyId
                )
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(deckVocabularyRepository, never())
                .deleteByIdDeckIdAndIdVocabularyId(
                        any(UUID.class),
                        any(UUID.class)
                );
    }

    @Test
    void removeVocabulary_shouldReturn404WhenDeckNotOwned() {
        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.removeVocabulary(
                        userId, deckId, vocabularyId
                )
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(deckVocabularyRepository);
    }

    @Test
    void getFlashcards_shouldReturnVocabularyList() {
        mockOwnedDeck();

        DeckVocabulary link = new DeckVocabulary(
                deckId,
                vocabularyId
        );

        when(deckVocabularyRepository
                .findByIdDeckIdOrderByAddedAtDesc(deckId))
                .thenReturn(List.of(link));

        mockOwnedVocabulary();

        List<VocabularyResponse> result =
                flashcardService.getFlashcards(userId, deckId);

        assertEquals(1, result.size());
        assertEquals("accomplish", result.get(0).word());
        assertEquals("complete", result.get(0).meaning());
    }

    @Test
    void getFlashcards_shouldReturnEmptyList() {
        mockOwnedDeck();

        when(deckVocabularyRepository
                .findByIdDeckIdOrderByAddedAtDesc(deckId))
                .thenReturn(List.of());

        List<VocabularyResponse> result =
                flashcardService.getFlashcards(userId, deckId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFlashcards_shouldReturn404WhenDeckNotOwned() {
        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> flashcardService.getFlashcards(userId, deckId)
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(deckVocabularyRepository);
    }

    private void mockOwnedDeck() {
        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.of(deck));
    }

    private void mockOwnedVocabulary() {
        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(vocabulary));
    }
}
