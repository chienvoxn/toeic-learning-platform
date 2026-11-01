import { Flag } from 'lucide-react';
import { AnswerOption } from '../molecules/AnswerOption';

export function QuestionPanel({ question, onSelectOption, onToggleFlag }) {
  if (!question) return null;
  return <article className="question-panel surface" id={`question-${question.id}`}><div className="question-heading"><strong>Câu {question.orderIndex}</strong><button type="button" className={`icon-button ${question.isFlagged ? 'flagged' : ''}`} aria-label="Đánh dấu câu hỏi" aria-pressed={!!question.isFlagged} onClick={() => onToggleFlag(question.id)}><Flag size={16} /></button></div><p>{question.content}</p><div className="answer-options" role="group" aria-label={`Đáp án câu ${question.orderIndex}`}>{question.options.map(option => <AnswerOption key={option.key} optionKey={option.key} text={option.text} isSelected={question.userAnswer === option.key} onSelect={onSelectOption} />)}</div></article>;
}
