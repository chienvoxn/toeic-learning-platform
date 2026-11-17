import { Flag } from 'lucide-react';
import { AnswerOption } from '../molecules/AnswerOption';

export function QuestionPanel({ question, onSelectOption, onToggleFlag, disabled = false }) {
  if (!question) return null;
  return <article className="question-panel surface" id={`question-${question.id}`}><div className="question-heading"><strong>Câu {question.orderIndex}</strong><button type="button" disabled={disabled} className={`icon-button ${question.isFlagged ? 'flagged' : ''}`} aria-label="Đánh dấu câu hỏi" aria-pressed={!!question.isFlagged} onClick={() => onToggleFlag(question.id)}><Flag size={16} /></button></div><p>{question.content}</p>{question.hasMedia && <p className="text-sm text-slate-500">Câu có media; phát media chưa được tích hợp.</p>}<div className="answer-options" role="group" aria-label={`Đáp án câu ${question.orderIndex}`}>{question.options.map(option => <AnswerOption key={option.key} disabled={disabled} optionKey={option.key} text={option.text} isSelected={question.userAnswer === option.key} onSelect={onSelectOption} />)}</div>{question.userAnswer && <button type="button" className="text-button mt-3" disabled={disabled} onClick={() => onSelectOption(null)}>Bỏ chọn</button>}</article>;
}
