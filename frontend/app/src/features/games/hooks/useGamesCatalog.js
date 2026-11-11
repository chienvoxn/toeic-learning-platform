import { gamesRepository } from '../repositories/games.mock';

export default function useGamesCatalog() {
  const games = gamesRepository.getGames();

  return { games };
}
