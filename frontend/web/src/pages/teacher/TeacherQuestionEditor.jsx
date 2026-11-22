
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Save,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react';

import { teacherQuestionsApi } from '../../features/teacher/teacherQuestions.api';

const PART_5_ID = 14; // ID thực tế trong toeic_db hiện tại

const initialChoices = [
  { label: 'A', content: '' },
  { label: 'B', content: '' },
  { label: 'C', content: '' },
  { label: 'D', content: '' },
];

export default function TeacherQuestionEditor() {
  const navigate = useNavigate();

  const [questionText, setQuestionText] = useState('');
  const [explanation, setExplanation] = useState('');
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [choices, setChoices] = useState(
    initialChoices.map(choice => ({ ...choice }))
  );
  const [correctAnswer, setCorrectAnswer] = useState('A');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  function updateChoice(index, content) {
    setChoices(current =>
      current.map((choice, i) =>
        i === index ? { ...choice, content } : choice
      )
    );
  }

  function validate() {
    if (!questionText.trim()) {
      return 'Vui lòng nhập nội dung câu hỏi.';
    }

    if (choices.some(choice => !choice.content.trim())) {
      return 'Vui lòng nhập đầy đủ 4 phương án A, B, C, D.';
    }

    return '';
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (saving) return;

    const validationError = validate();

    if (validationError) {
      setError(validationError);
      return;
    }

    setSaving(true);
    setError('');
    setSuccess('');

    const payload = {
      partId: PART_5_ID,
      questionText: questionText.trim(),
      explanation: explanation.trim() || null,
      difficulty,
      choices: choices.map((choice, index) => ({
        label: choice.label,
        content: choice.content.trim(),
        isCorrect: choice.label === correctAnswer,
        displayOrder: index + 1,
      })),
    };

    try {
      const result = await teacherQuestionsApi.create(payload);

      setSuccess(
        `Đã lưu bản nháp ${result.question?.questionId ?? ''}`
      );

      navigate('/teacher/questions', {
        replace: true,
      });
    } catch (err) {
      setError(
        err.message ||
        'Không thể lưu câu hỏi. Vui lòng kiểm tra Backend.'
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="teacher-question-editor">
      <button
        type="button"
        className="teacher-editor-back"
        onClick={() => navigate('/teacher/questions')}
      >
        <ArrowLeft size={18} />
        Quay lại ngân hàng câu hỏi
      </button>

      <div className="teacher-page-heading">
        <h1>Tạo câu hỏi TOEIC</h1>
        <p>
          Part 5 — Incomplete Sentences.
          Câu hỏi mới sẽ được lưu dưới dạng bản nháp.
        </p>
      </div>

      <form
        className="teacher-editor-form"
        onSubmit={handleSubmit}
      >
        <div className="teacher-editor-section">
          <h2>Thông tin câu hỏi</h2>

          <div className="teacher-editor-grid">
            <label>
              TOEIC Part
              <input
                value="Part 5 — Incomplete Sentences"
                readOnly
              />
            </label>

            <label>
              Độ khó
              <select
                value={difficulty}
                onChange={event =>
                  setDifficulty(event.target.value)
                }
              >
                <option value="EASY">Dễ</option>
                <option value="MEDIUM">Trung bình</option>
                <option value="HARD">Khó</option>
              </select>
            </label>
          </div>

          <label>
            Nội dung câu hỏi *
            <textarea
              value={questionText}
              onChange={event =>
                setQuestionText(event.target.value)
              }
              rows={4}
              placeholder="The manager _____ the report yesterday."
              required
            />
          </label>
        </div>

        <div className="teacher-editor-section">
          <h2>Đáp án trắc nghiệm</h2>
          <p className="teacher-editor-hint">
            Nhập đủ bốn lựa chọn và đánh dấu một
            phương án đúng.
          </p>

          <div className="teacher-editor-choices">
            {choices.map((choice, index) => (
              <div
                className="teacher-editor-choice"
                key={choice.label}
              >
                <span className="teacher-choice-label">
                  {choice.label}
                </span>

                <input
                  type="text"
                  value={choice.content}
                  onChange={event =>
                    updateChoice(index, event.target.value)
                  }
                  placeholder={`Nội dung đáp án ${choice.label}`}
                  required
                />

                <label className="teacher-correct-choice">
                  <input
                    type="radio"
                    name="correctAnswer"
                    value={choice.label}
                    checked={correctAnswer === choice.label}
                    onChange={() =>
                      setCorrectAnswer(choice.label)
                    }
                  />
                  Đúng
                </label>
              </div>
            ))}
          </div>
        </div>

        <div className="teacher-editor-section">
          <h2>Giải thích đáp án</h2>

          <textarea
            value={explanation}
            onChange={event =>
              setExplanation(event.target.value)
            }
            rows={5}
            placeholder="Giải thích tại sao đáp án này đúng..."
          />
        </div>

        {error && (
          <div className="teacher-editor-error" role="alert">
            <AlertCircle size={18} />
            {error}
          </div>
        )}

        {success && (
          <div className="teacher-editor-success" role="status">
            <CheckCircle2 size={18} />
            {success}
          </div>
        )}

        <div className="teacher-editor-actions">
          <button
            type="button"
            className="teacher-outline-button"
            onClick={() => navigate('/teacher/questions')}
            disabled={saving}
          >
            Hủy
          </button>

          <button
            type="submit"
            className="teacher-primary-button"
            disabled={saving}
          >
            <Save size={18} />
            {saving ? 'Đang lưu...' : 'Lưu bản nháp'}
          </button>
        </div>
      </form>
    </div>
  );
}
