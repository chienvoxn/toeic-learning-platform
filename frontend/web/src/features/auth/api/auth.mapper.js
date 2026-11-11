export function toClientUser(dto) {
  if (!dto) return null;
  return {
    id: dto.userId, userId: dto.userId, email: dto.email, fullName: dto.fullName,
    avatarUrl: dto.avatarUrl ?? null, targetScore: dto.targetScore ?? null,
    roles: dto.roles ?? [], status: dto.status,
    emailVerifiedAt: dto.emailVerifiedAt, lastLoginAt: dto.lastLoginAt,
  };
}

// Demo-only fields never enter the remote mapper.
export function toDemoUser(dto) {
  return { id: dto.id, userId: dto.id, email: dto.email, fullName: dto.full_name,
    targetScore: dto.target_score ?? null, streakDays: dto.streak_days, roles: dto.roles };
}
