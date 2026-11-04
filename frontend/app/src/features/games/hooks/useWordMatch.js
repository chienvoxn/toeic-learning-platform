import { useEffect, useRef, useState } from "react";
import { getWordPairs } from "../repositories/wordMatch.mock";

export default function useWordMatch() {
  const [gameState, setGameState] = useState("ready"); // 'ready' | 'playing' | 'time-up' | 'completed'
  const [cards, setCards] = useState([]);
  const [selectedCards, setSelectedCards] = useState([]);
  const [clearedPairIds, setClearedPairIds] = useState(new Set());
  const [mismatchedIds, setMismatchedIds] = useState([]);
  const [score, setScore] = useState(0);
  const [timeLeft, setTimeLeft] = useState(45);
  const [paused, setPaused] = useState(false);
  const [sound, setSound] = useState(true);
  const timerRef = useRef(null);
  const mismatchRef = useRef(null);

  const initGame = () => {
    clearTimeout(mismatchRef.current);
    setPaused(false);
    const list = [];
    getWordPairs().forEach((p) => {
      list.push({ cardId: `${p.id}-en`, pairId: p.id, text: p.en });
      list.push({ cardId: `${p.id}-vi`, pairId: p.id, text: p.vi });
    });
    setCards(list.sort(() => 0.5 - Math.random()));
    setClearedPairIds(new Set());
    setSelectedCards([]);
    setMismatchedIds([]);
    setScore(0);
    setTimeLeft(45);
    setGameState("ready");
  };

  useEffect(() => {
    initGame();
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      clearTimeout(mismatchRef.current);
    };
  }, []);

  useEffect(() => {
    if (gameState === "playing" && !paused) {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            setGameState("time-up");
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
  }, [gameState, paused]);

  const handleCardClick = (card) => {
    if (gameState !== "playing" || paused) return;
    if (clearedPairIds.has(card.pairId)) return;
    if (selectedCards.some((item) => item.cardId === card.cardId)) return;
    if (selectedCards.length >= 2) return;

    const newSelected = [...selectedCards, card];
    setSelectedCards(newSelected);

    if (newSelected.length === 2) {
      const [first, second] = newSelected;
      if (first.pairId === second.pairId) {
        // MATCH!
        const nextSet = new Set(clearedPairIds);
        nextSet.add(first.pairId);
        setClearedPairIds(nextSet);
        setScore((prev) => prev + 10);
        setSelectedCards([]);

        if (nextSet.size >= cards.length / 2) {
          setGameState("completed");
        }
      } else {
        // MISMATCH!
        setMismatchedIds([first.cardId, second.cardId]);
        mismatchRef.current = setTimeout(() => {
          setSelectedCards([]);
          setMismatchedIds([]);
        }, 500);
      }
    }
  };

  const isTimeRunningOut = timeLeft <= 10 && gameState === "playing";

  return {
    gameState,
    setGameState,
    cards,
    selectedCards,
    clearedPairIds,
    mismatchedIds,
    score,
    timeLeft,
    initGame,
    handleCardClick,
    isTimeRunningOut,
    paused,
    setPaused,
    sound,
    setSound,
  };
}
