import { useState, useEffect, useRef } from 'react';
import { gameRepo } from '../repositories/game.repo';

export function useWordMatch() {
  const [gameState, setGameState] = useState('ready'); // 'ready' | 'playing' | 'time-up' | 'completed'
  const [cards, setCards] = useState([]);
  const [selectedCards, setSelectedCards] = useState([]); // [cardId, cardId]
  const [clearedPairIds, setClearedPairIds] = useState(new Set());
  const [mismatchedCardIds, setMismatchedCardIds] = useState([]);
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [timeLeft, setTimeLeft] = useState(45);
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState(null);

  const sessionIdRef = useRef('');
  const timerRef = useRef(null);

  const initGame = async () => {
    setLoading(true);
    try {
      const session = await gameRepo.getWordMatchSession(6);
      sessionIdRef.current = session.sessionId;
      setCards(session.cards);
      setTimeLeft(session.timeLimitSeconds || 45);
      setClearedPairIds(new Set());
      setSelectedCards([]);
      setMismatchedCardIds([]);
      setScore(0);
      setStreak(0);
      setSummary(null);
      setGameState('ready');
    } catch (err) {
      console.error('Không thể tạo phiên trò chơi:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    initGame();
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const finishGame = async (endState, finalScore = score, clearedCount = clearedPairIds.size) => {
    setGameState(endState);
    if (timerRef.current) clearInterval(timerRef.current);

    try {
      const result = await gameRepo.submitGameResult(sessionIdRef.current, finalScore, clearedCount);
      setSummary(result);
    } catch (err) {
      console.error('Lỗi khi nộp kết quả game:', err);
    }
  };

  // Timer runner when gameState === 'playing'
  useEffect(() => {
    if (gameState === 'playing') {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            finishGame('time-up');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [gameState]);

  const startGame = () => {
    setGameState('playing');
  };

  const handleCardClick = (card) => {
    if (gameState !== 'playing') return;
    if (clearedPairIds.has(card.pairId)) return;
    if (selectedCards.some((c) => c.cardId === card.cardId)) return;
    if (selectedCards.length >= 2) return;

    const newSelection = [...selectedCards, card];
    setSelectedCards(newSelection);

    if (newSelection.length === 2) {
      const [first, second] = newSelection;

      if (first.pairId === second.pairId) {
        // MATCH!
        const newCleared = new Set(clearedPairIds);
        newCleared.add(first.pairId);
        setClearedPairIds(newCleared);

        const earnedPoints = 100 + streak * 20;
        setScore((prev) => prev + earnedPoints);
        setStreak((prev) => prev + 1);
        setSelectedCards([]);

        // Check if all pairs cleared
        const totalPairs = cards.length / 2;
        if (newCleared.size >= totalPairs) {
          finishGame('completed', score + earnedPoints, newCleared.size);
        }
      } else {
        // MISMATCH!
        setMismatchedCardIds([first.cardId, second.cardId]);
        setStreak(0);
        setTimeout(() => {
          setSelectedCards([]);
          setMismatchedCardIds([]);
        }, 650);
      }
    }
  };

  return {
    gameState,
    cards,
    selectedCards,
    clearedPairIds,
    mismatchedCardIds,
    score,
    streak,
    timeLeft,
    loading,
    summary,
    startGame,
    handleCardClick,
    restartGame: initGame,
  };
}
