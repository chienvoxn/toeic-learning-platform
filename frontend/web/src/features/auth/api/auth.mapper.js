export function toClientUser(dto) {
  if (!dto) return null;
  return {
    id: dto.userId, userId: dto.userId, email: dto.email, fullName: dto.fullName,
    avatarUrl: dto.avatarUrl ?? null, targetScore: dto.targetScore ?? null,
    roles: dto.roles ?? [], status: dto.status,
    localLoginEnabled: dto.localLoginEnabled ?? true,
    emailVerifiedAt: dto.emailVerifiedAt, lastLoginAt: dto.lastLoginAt,
    ...(dto.adminCapabilities ? { adminCapabilities: {
      userManagement: dto.adminCapabilities.userManagement === true,
      rbacManagement: dto.adminCapabilities.rbacManagement === true,
      auditRead: dto.adminCapabilities.auditRead === true,
      healthRead: dto.adminCapabilities.healthRead === true,
      usageRead: dto.adminCapabilities.usageRead === true,
      settingsManagement: dto.adminCapabilities.settingsManagement === true,
    } } : {}),
  };
}

// Demo-only fields never enter the remote mapper.
export function toDemoUser(dto) {
  return { id: dto.id, userId: dto.id, email: dto.email, fullName: dto.full_name,
    targetScore: dto.target_score ?? null, streakDays: dto.streak_days, roles: dto.roles };
}
