export function toClientContent(dto) {
  if (!dto) return null;
  return {
    id: dto.id,
    title: dto.title,
    description: dto.description || '',
    category: dto.category || 'General',
    part: dto.part || 1,
    difficulty: dto.difficulty || 'B1',
    mediaType: dto.media_type || 'video',
    mediaUrl: dto.media_url || '',
    thumbnailUrl: dto.thumbnail_url || '',
    sourcePlatform: dto.source_platform || 'YouTube',
    durationMinutes: dto.duration_minutes || 5,
    totalViews: dto.total_views || 0,
    isBookmarked: Boolean(dto.is_bookmarked),
  };
}

export function toClientContentList(dtoList) {
  if (!Array.isArray(dtoList)) return [];
  return dtoList.map(toClientContent);
}
