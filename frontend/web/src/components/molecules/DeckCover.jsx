export function DeckCover({ title, index = 0, category = '' }) {
  const common = title.startsWith('1000');
  return <div className={`deck-cover cover-${index % 4} ${common ? 'cover-common' : ''}`}><div className="deck-cover-book"><strong>{common ? '1000' : title.startsWith('600') ? '600' : 'TOEIC'}</strong><span>{common ? 'ENGLISH' : category || 'VOCABULARY'}</span><small>{common ? 'VOCABULARY' : title.split('(')[0]}</small></div></div>;
}
