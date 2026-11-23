
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  ClipboardList,
  Plus,
  Search,
  RefreshCw,
  X,
  Save,
  FileText,
  Clock3,
  ListChecks,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react';

import { teacherExamsApi } from '../../features/teacher/teacherExams.api';

const STATUS_LABELS = {
  DRAFT: 'Bản nháp',
  PUBLISHED: 'Đã xuất bản',
  ARCHIVED: 'Đã lưu trữ',
  RETIRED: 'Ngừng sử dụng',
};

export default function TeacherExamsPage() {
  const [tests, setTests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [refresh, setRefresh] = useState(0);

  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState('');

  // ===================== LOAD TESTS =====================
  useEffect(() => {
    let active = true;

    async function loadTests() {
      setLoading(true);
      setError('');

      try {
        const data = await teacherExamsApi.list();

        if (!active) return;

        if (!Array.isArray(data)) {
          throw new Error('Định dạng danh sách đề thi không hợp lệ.');
        }

        setTests(data);
      } catch (err) {
        if (!active) return;

        setTests([]);
        setError(
          err.message ||
          'Không thể tải danh sách đề thi.'
        );
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadTests();

    return () => {
      active = false;
    };
  }, [refresh]);

  // ===================== CREATE TEST =====================
  async function handleCreate(event) {
    event.preventDefault();

    if (saving) return;

    if (!title.trim()) {
      setFormError('Vui lòng nhập tên đề thi.');
      return;
    }

    if (title.length > 255) {
      setFormError('Tên đề thi không được vượt quá 255 ký tự.');
      return;
    }

    setSaving(true);
    setFormError('');
    setSuccess('');

    try {
      const created = await teacherExamsApi.create({
        title: title.trim(),
        description: description.trim(),
      });

      setShowCreate(false);
      setTitle('');
      setDescription('');

      setSuccess(
        `Đã tạo bản nháp "${created.title}".`
      );

      setSearch('');
      setRefresh(value => value + 1);
    } catch (err) {
      setFormError(
        err.message ||
        'Không thể tạo đề thi. Vui lòng thử lại.'
      );
    } finally {
      setSaving(false);
    }
  }

  function closeCreate() {
    if (saving) return;

    setShowCreate(false);
    setFormError('');
  }

  const filteredTests = tests.filter(test => {
    const keyword = search.trim().toLowerCase();

    return (
      String(test.title ?? '').toLowerCase().includes(keyword) ||
      String(test.testId ?? '').toLowerCase().includes(keyword)
    );
  });

  const draftCount = tests.filter(
    test => test.status === 'DRAFT'
  ).length;

  const publishedCount = tests.filter(
    test => test.status === 'PUBLISHED'
  ).length;

  return (
    <div className="teacher-exams-page">
      {/* ===================== HEADER ===================== */}
      <div className="teacher-page-heading teacher-page-toolbar">
        <div>
          <h1>Ngân hàng đề thi</h1>
          <p>
            Quản lý đề thi TOEIC Listening & Reading,
            tạo bản nháp và chuẩn bị nội dung Full Test.
          </p>
        </div>

        <button
          type="button"
          className="teacher-primary-button"
          onClick={() => {
            setFormError('');
            setShowCreate(true);
          }}
        >
          <Plus size={18} />
          Tạo đề thi
        </button>
      </div>

      {/* ===================== STATISTICS ===================== */}
      <div className="teacher-exams-stats">
        <div className="teacher-exams-stat">
          <ClipboardList size={25} />
          <span>Tổng số đề</span>
          <strong>{tests.length}</strong>
        </div>

        <div className="teacher-exams-stat">
          <FileText size={25} />
          <span>Đề nháp</span>
          <strong>{draftCount}</strong>
        </div>

        <div className="teacher-exams-stat">
          <CheckCircle2 size={25} />
          <span>Đã xuất bản</span>
          <strong>{publishedCount}</strong>
        </div>
      </div>

      {/* ===================== MESSAGES ===================== */}
      {success && (
        <div
          className="teacher-editor-success"
          role="status"
          style={{ marginBottom: 20 }}
        >
          <CheckCircle2 size={18} />
          {success}
        </div>
      )}

      {/* ===================== TEST LIST ===================== */}
      <section className="teacher-content-panel">
        <div className="teacher-filter-bar">
          <label className="teacher-search-field">
            <Search size={18} />

            <input
              type="search"
              placeholder="Tìm đề thi theo tên hoặc ID..."
              aria-label="Tìm đề thi"
              value={search}
              onChange={event => setSearch(event.target.value)}
            />
          </label>

          <button
            type="button"
            className="teacher-outline-button"
            onClick={() => setRefresh(value => value + 1)}
            aria-label="Tải lại danh sách đề thi"
          >
            <RefreshCw size={18} />
            Làm mới
          </button>
        </div>

        <div className="teacher-list-summary">
          <strong>Danh sách đề thi</strong>
          <span>
            {filteredTests.length} / {tests.length} đề
          </span>
        </div>

        {loading && (
          <div className="teacher-empty-state" role="status">
            Đang tải danh sách đề thi từ Backend...
          </div>
        )}

        {!loading && error && (
          <div className="teacher-error-state" role="alert">
            <AlertCircle size={22} />
            {error}
          </div>
        )}

        {!loading && !error && filteredTests.length === 0 && (
          <div className="teacher-empty-state">
            <ClipboardList size={38} />

            <h2>
              {tests.length === 0
                ? 'Chưa có đề thi nào'
                : 'Không tìm thấy đề thi phù hợp'}
            </h2>

            <p>
              {tests.length === 0
                ? 'Tạo đề thi đầu tiên để bắt đầu xây dựng ngân hàng TOEIC.'
                : 'Thử tìm bằng tên hoặc mã đề thi khác.'}
            </p>

            {tests.length === 0 && (
              <button
                type="button"
                className="teacher-primary-button"
                onClick={() => setShowCreate(true)}
              >
                <Plus size={17} />
                Tạo đề thi đầu tiên
              </button>
            )}
          </div>
        )}

        {!loading && !error && filteredTests.length > 0 && (
          <div className="teacher-exams-list">
            {filteredTests.map(test => (
              <article
                className="teacher-exams-item"
                key={test.testId}
              >
                <div className="teacher-exams-item-icon">
                  <ClipboardList size={25} />
                </div>

                <div className="teacher-exams-item-info">
                  <h3>{test.title}</h3>

                  <p>
                    {test.description ||
                      'Chưa có mô tả đề thi.'}
                  </p>

                  <div className="teacher-exams-item-meta">
                    <span>
                      <FileText size={14} />
                      {test.testType || '—'}
                    </span>

                    <span>
                      <Clock3 size={14} />
                      Chưa có dữ liệu thời gian phiên bản
                    </span>
                  </div>

                  <small>ID: {test.testId}</small>
                </div>

                <div className="teacher-exams-item-actions">
                  <span
                    className={`teacher-status-pill ${
                      test.status || ''
                    }`}
                  >
                    {STATUS_LABELS[test.status] ||
                      test.status}
                  </span>

                  {test.status === 'DRAFT' && (
                    <div className="teacher-exams-pending-actions">
                      <button
                        type="button"
                        className="teacher-outline-button"
                        disabled
                        title="Chờ API validate đề thi từ backend"
                      >
                        Kiểm tra đề
                      </button>
                      <button
                        type="button"
                        className="teacher-primary-button"
                        disabled
                        title="Chờ API xuất bản đề thi từ backend"
                      >
                        Xuất bản
                      </button>
                    </div>
                  )}
                  <span className="teacher-exams-awaiting">
                    Biên soạn, kiểm tra và xuất bản cần API backend tương ứng.
                  </span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {/* ===================== QUESTION BANK ===================== */}
      <section
        className="teacher-content-panel"
        style={{ marginTop: 20 }}
      >
        <div className="teacher-exam-section">
          <h2>Ngân hàng câu hỏi</h2>

          <div className="teacher-exam-link-row">
            <ListChecks size={25} />

            <div>
              <strong>Câu hỏi TOEIC Part 1–7</strong>

              <p>
                Quản lý câu hỏi và đáp án, chuẩn bị
                cho chức năng biên soạn đề thi.
              </p>
            </div>

            <Link
              to="/teacher/questions"
              className="teacher-outline-button"
            >
              Mở
            </Link>
          </div>
        </div>
      </section>

      {/* ===================== CREATE MODAL ===================== */}
      {showCreate && (
        <div
          className="teacher-modal-backdrop"
          onMouseDown={event => {
            if (event.target === event.currentTarget) {
              closeCreate();
            }
          }}
        >
          <section
            className="teacher-question-detail teacher-exam-create-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Tạo đề thi TOEIC"
          >
            <div className="teacher-question-detail-header">
              <h2>Tạo đề thi TOEIC</h2>

              <button
                type="button"
                onClick={closeCreate}
                disabled={saving}
                aria-label="Đóng biểu mẫu"
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleCreate}>
              <div className="teacher-exam-form-field">
                <label htmlFor="exam-title">
                  Tên đề thi *
                </label>

                <input
                  id="exam-title"
                  type="text"
                  value={title}
                  maxLength={255}
                  required
                  onChange={event =>
                    setTitle(event.target.value)
                  }
                  placeholder="VD: TOEIC LR Collection 1 - Test 1"
                />
              </div>

              <div className="teacher-exam-form-field">
                <label htmlFor="exam-description">
                  Mô tả
                </label>

                <textarea
                  id="exam-description"
                  rows={4}
                  value={description}
                  onChange={event =>
                    setDescription(event.target.value)
                  }
                  placeholder="Mô tả nội dung đề thi..."
                />
              </div>

              <div className="teacher-exam-draft-info">
                <FileText size={20} />

                <p>
                  Đề thi sẽ được tạo ở trạng thái
                  DRAFT với phiên bản số 1.
                  Chưa có câu hỏi và chưa thể xuất bản.
                </p>
              </div>

              {formError && (
                <div
                  className="teacher-editor-error"
                  role="alert"
                >
                  <AlertCircle size={18} />
                  {formError}
                </div>
              )}

              <div
                className="teacher-editor-actions"
                style={{ marginTop: 24 }}
              >
                <button
                  type="button"
                  className="teacher-outline-button"
                  disabled={saving}
                  onClick={closeCreate}
                >
                  Hủy
                </button>

                <button
                  type="submit"
                  className="teacher-primary-button"
                  disabled={saving}
                >
                  <Save size={18} />
                  {saving
                    ? 'Đang lưu...'
                    : 'Lưu bản nháp'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
