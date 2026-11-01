export function AnswerOption({ optionKey, text, isSelected, onSelect, disabled = false }) {
  return <button type="button" disabled={disabled} onClick={() => onSelect(optionKey)} aria-pressed={isSelected} className={`answer-option ${isSelected ? 'selected' : ''}`}><span>{optionKey}</span><span>{text}</span></button>;
}
