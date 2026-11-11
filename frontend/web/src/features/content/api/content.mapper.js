export function toClientContent(dto) {
  if (!dto) return null;
  return {
    id: dto.contentId ?? dto.id,
    versionId: dto.contentVersionId,
    title: dto.title,
    description: dto.summary ?? dto.description ?? '',
    body: dto.body || '',
    category: typeof dto.category === 'object' ? dto.category?.name : dto.category || '',
    part: dto.part,
    difficulty: dto.difficulty || '',
    media: dto.media || [],
    mediaType: dto.media_type || 'text',
    mediaUrl: dto.media_url || '', thumbnailUrl: dto.thumbnail_url || '',
    sourcePlatform: dto.source_platform || '', durationMinutes: dto.duration_minutes ?? null,
    totalViews: dto.total_views ?? null,
    isBookmarked: Boolean(dto.is_bookmarked),
  };
}
export function toClientContentList(items) { return Array.isArray(items) ? items.map(toClientContent) : []; }
