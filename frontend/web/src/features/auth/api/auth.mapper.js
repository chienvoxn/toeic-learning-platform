export function toClientUser(dto) {
  if (!dto) return null;
  return {
    id: dto.userId ?? dto.id,
    email: dto.email,
    fullName: dto.full_name || dto.fullName,
    avatarUrl: dto.avatar_url || dto.avatarUrl,
    targetScore: dto.target_score ?? dto.targetScore ?? null,
    roles: dto.roles || ['LEARNER'],
    streakDays: dto.streak_days ?? dto.streakDays ?? 0,
    status: dto.status,
    localLoginEnabled: dto.localLoginEnabled ?? true,
  };
}
