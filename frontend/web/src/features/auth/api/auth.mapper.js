export function toClientUser(dto) {
  if (!dto) return null;
  return {
    id: dto.id,
    email: dto.email,
    fullName: dto.full_name || dto.fullName,
    avatarUrl: dto.avatar_url || dto.avatarUrl,
    targetScore: dto.target_score || dto.targetScore || 800,
    roles: dto.roles || ['LEARNER'],
    streakDays: dto.streak_days || 12,
  };
}
