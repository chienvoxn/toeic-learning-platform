import { Link } from 'react-router-dom';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';

export function VideoLessonCard({ lesson }) {
  return <Link className="video-lesson-card surface" to={`/listening?mode=shadowing&view=exercise&lesson=${lesson.id}`}><div className="video-thumbnail"><FigmaAsset screen="video-library" name={lesson.image} alt={lesson.title} /><span className="video-views">◉ {lesson.views}</span><span className="lesson-level">{lesson.level}</span><span className="lesson-source">▶ YouTube</span><span className="video-duration">◷ {lesson.duration}</span></div><div className="video-lesson-caption"><h3>{lesson.title}</h3><span>Shadowing ×</span></div></Link>;
}
