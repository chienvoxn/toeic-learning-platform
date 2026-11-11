
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateDeckRequest;
import com.toeic.backend.vocabulary.api.DeckResponse;
import com.toeic.backend.vocabulary.domain.VocabularyDeck;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;

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
class VocabularyDeckServiceTest {

    @Mock
    private VocabularyDeckRepository deckRepository;

    @InjectMocks
    private VocabularyDeckService deckService;

    private UUID userId;
    private VocabularyDeck deck;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        deck = new VocabularyDeck(
                userId,
                "TOEIC Business",
                "Business vocabulary"
        );
    }

    @Test
    void create_shouldCreateDeck() {
        CreateDeckRequest request = new CreateDeckRequest(
                " TOEIC Business ",
                "Business vocabulary"
        );

        when(deckRepository.save(any(VocabularyDeck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeckResponse response = deckService.create(userId, request);

        assertNotNull(response);
        assertEquals("TOEIC Business", response.name());
        assertEquals("Business vocabulary", response.description());
        assertNotNull(response.deckId());

        verify(deckRepository).save(any(VocabularyDeck.class));
    }

    @Test
    void getAll_shouldReturnOwnedDecks() {
        when(deckRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(deck));

        List<DeckResponse> result = deckService.getAll(userId);

        assertEquals(1, result.size());
        assertEquals("TOEIC Business", result.get(0).name());

        verify(deckRepository)
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void getAll_shouldReturnEmptyList() {
        when(deckRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        List<DeckResponse> result = deckService.getAll(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getById_shouldReturnOwnedDeck() {
        UUID deckId = deck.getDeckId();

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.of(deck));

        DeckResponse response = deckService.getById(userId, deckId);

        assertEquals(deckId, response.deckId());
        assertEquals("TOEIC Business", response.name());
    }

    @Test
    void getById_shouldReturn404WhenNotFound() {
        UUID deckId = UUID.randomUUID();

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> deckService.getById(userId, deckId)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void getById_shouldReturn404ForOtherUserDeck() {
        UUID deckId = UUID.randomUUID();

        // Repository filters by both deckId and userId.
        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> deckService.getById(userId, deckId)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void update_shouldUpdateOwnedDeck() {
        UUID deckId = deck.getDeckId();

        CreateDeckRequest request = new CreateDeckRequest(
                " Updated Business ",
                "Updated description"
        );

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.of(deck));

        when(deckRepository.saveAndFlush(any(VocabularyDeck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeckResponse response = deckService.update(
                userId,
                deckId,
                request
        );

        assertEquals("Updated Business", response.name());
        assertEquals("Updated description", response.description());

        verify(deckRepository)
                .saveAndFlush(any(VocabularyDeck.class));
    }

    @Test
    void update_shouldReturn404ForOtherUserDeck() {
        UUID deckId = UUID.randomUUID();

        CreateDeckRequest request = new CreateDeckRequest(
                "Modified",
                "Modified description"
        );

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> deckService.update(userId, deckId, request)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(deckRepository, never())
                .saveAndFlush(any(VocabularyDeck.class));
    }

    @Test
    void delete_shouldDeleteOwnedDeck() {
        UUID deckId = deck.getDeckId();

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.of(deck));

        deckService.delete(userId, deckId);

        verify(deckRepository).delete(deck);
    }

    @Test
    void delete_shouldReturn404ForOtherUserDeck() {
        UUID deckId = UUID.randomUUID();

        when(deckRepository.findByDeckIdAndUserId(deckId, userId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> deckService.delete(userId, deckId)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(deckRepository, never())
                .delete(any(VocabularyDeck.class));
    }
}
