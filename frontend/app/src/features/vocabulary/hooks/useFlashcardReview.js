import { useState } from 'react';
import { vocabularyRepository } from '../repositories/vocabulary.mock';

export default function useFlashcardReview() {
  const cards = vocabularyRepository.getReviewCards();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isRevealed, setIsRevealed] = useState(false);
  const [completed, setCompleted] = useState(false);

  const current = cards[currentIndex];

  const handleRate = () => {
    if (currentIndex + 1 < cards.length) {
      setCurrentIndex(prev => prev + 1);
      setIsRevealed(false);
    } else {
      setCompleted(true);
    }
  };

  const handleRestart = () => {
    setCurrentIndex(0);
    setIsRevealed(false);
    setCompleted(false);
  };

  return { cards, currentIndex, isRevealed, setIsRevealed, completed, current, handleRate, handleRestart };
}
