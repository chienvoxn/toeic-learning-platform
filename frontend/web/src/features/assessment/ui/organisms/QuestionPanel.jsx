import { Flag } from 'lucide-react';
import { AnswerOption } from '../molecules/AnswerOption';
import { ExamMedia } from '../ExamMedia';

export function QuestionPanel({ question, onSelectOption, onToggleFlag, disabled = false }) {
  if (!question) return null;
  return <article className="question-panel surface" id={`question-${question.id}`}><div className="question-heading"><strong>Câu {question.orderIndex}</strong><button type="button" disabled={disabled} className={`icon-button ${question.isFlagged ? 'flagged' : ''}`} aria-label="Đánh dấu câu hỏi" aria-pressed={!!question.isFlagged} onClick={() => onToggleFlag(question.id)}><Flag size={16} /></button></div>{question.instruction && <p>{question.instruction}</p>}{question.passage && <div className="exam-passage" style={{whiteSpace:"pre-wrap"}}>{question.passage}</div>}{question.assets?.map(a => <ExamMedia key={a.resourceId} {...a} />)}<p>{question.content}</p><div className="answer-options" role="group" aria-label={`Đáp án câu ${question.orderIndex}`}>{question.options.map(option => <AnswerOption key={option.key} disabled={disabled} optionKey={option.key} text={option.text} isSelected={question.userAnswer === option.key} onSelect={onSelectOption} />)}</div></article>;
}
