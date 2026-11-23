
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  ListChecks,
  Search,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Eye,
  Plus,
  X,
  AlertCircle,
} from 'lucide-react';

import { teacherQuestionsApi } from '../../features/teacher/teacherQuestions.api';

const statusLabels = {
  DRAFT: 'Bản nháp',
  PUBLISHED: 'Đã xuất bản',
  ARCHIVED: 'Đã lưu trữ',
};

function formatDate(value) {
  if (!value) return '—';

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString('vi-VN');
}

export default function TeacherQuestionsPage() {
  // ==================== LIST STATE ====================
  const [questions, setQuestions] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [revision, setRevision] = useState(0);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // ==================== DETAIL STATE ====================
  const [selected, setSelected] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);

  // ==================== LOAD LIST ====================
  useEffect(() => {
    let active = true;

    async function loadQuestions() {
      setLoading(true);
      setError('');

      try {
        const data = await teacherQuestionsApi.list({
          page,
          size: 20,
          status,
        });

        if (!active) return;

        setQuestions(data.content ?? []);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      } catch (err) {
        if (!active) return;

        setQuestions([]);
        setTotalPages(0);
        setTotalElements(0);

        setError(
          err.message ||
          'Không tải được danh sách câu hỏi.'
        );
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadQuestions();

    return () => {
      active = false;
    };
  }, [page, status, revision]);

  // ==================== OPEN DETAIL ====================
  async function openDetail(id) {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError('');
    setSelected(null);

    try {
      const data = await teacherQuestionsApi.detail(id);
      setSelected(data);
    } catch (err) {
      setDetailError(
        err.message ||
        'Không thể tải chi tiết câu hỏi.'
      );
    } finally {
      setDetailLoading(false);
    }
  }

  function closeDetail() {
    setDetailOpen(false);
    setSelected(null);
    setDetailError('');
  }

  // ==================== FILTER CURRENT PAGE ====================
  // Backend list endpoint does not support a 'q' parameter.
  // Search here only filters currently loaded question IDs.
  const visibleQuestions = questions.filter(question =>
    String(question.questionId ?? '')
      .toLowerCase()
      .includes(search.trim().toLowerCase())
  );

  // ==================== RENDER ====================
  return (
    <div className="teacher-questions-page">
      {/* PAGE HEADING */}
      <div className="teacher-page-heading teacher-page-toolbar">
        <div>
          <h1>Ngân hàng câu hỏi</h1>

          <p>
            Quản lý câu hỏi TOEIC Listening & Reading,
            đáp án, lời giải và các phiên bản biên soạn.
          </p>
        </div>

        <Link
          to="/teacher/questions/new"
          className="teacher-primary-button"
          style={{ textDecoration: 'none' }}
        >
          <Plus size={18} />
          Tạo câu hỏi
        </Link>
      </div>

      {/* QUESTION LIST */}
      <section className="teacher-content-panel">
        {/* FILTER BAR */}
        <div className="teacher-filter-bar">
          <label className="teacher-search-field">
            <Search size={18} />

            <input
              type="search"
              placeholder="Tìm ID trong trang hiện tại..."
              aria-label="Tìm ID câu hỏi trong trang hiện tại"
              value={search}
              onChange={event =>
                setSearch(event.target.value)
              }
            />
          </label>

          <select
            aria-label="Lọc theo trạng thái"
            value={status}
            onChange={event => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">
              Tất cả trạng thái
            </option>
            <option value="DRAFT">
              Bản nháp
            </option>
            <option value="PUBLISHED">
              Đã xuất bản
            </option>
            <option value="ARCHIVED">
              Đã lưu trữ
            </option>
          </select>

          <button
            type="button"
            className="teacher-outline-button"
            onClick={() =>
              setRevision(value => value + 1)
            }
            aria-label="Làm mới danh sách"
            title="Làm mới danh sách"
          >
            <RefreshCw size={18} />
          </button>
        </div>

        {/* LIST SUMMARY */}
        <div className="teacher-list-summary">
          <strong>Danh sách câu hỏi</strong>

          <span>
            {totalElements} câu hỏi theo bộ lọc
          </span>
        </div>

        {/* LOADING */}
        {loading && (
          <div
            className="teacher-empty-state"
            role="status"
          >
            Đang tải câu hỏi từ Backend...
          </div>
        )}

        {/* ERROR */}
        {!loading && error && (
          <div
            className="teacher-error-state"
            role="alert"
          >
            <AlertCircle size={22} />
            <span>{error}</span>
          </div>
        )}

        {/* EMPTY */}
        {!loading &&
          !error &&
          visibleQuestions.length === 0 && (
            <div className="teacher-empty-state">
              <ListChecks size={36} />

              <h2>
                Chưa có câu hỏi phù hợp
              </h2>

              <p>
                Bạn có thể tạo câu hỏi TOEIC Part 5
                để bắt đầu xây dựng ngân hàng đề thi.
              </p>

              <Link
                to="/teacher/questions/new"
                className="teacher-primary-button"
                style={{ textDecoration: 'none' }}
              >
                <Plus size={17} />
                Tạo câu hỏi đầu tiên
              </Link>
            </div>
          )}

        {/* QUESTION TABLE */}
        {!loading &&
          !error &&
          visibleQuestions.length > 0 && (
            <div className="teacher-table-scroll">
              <table className="teacher-data-table">
                <thead>
                  <tr>
                    <th>Mã câu hỏi</th>
                    <th>Part ID</th>
                    <th>Loại câu hỏi</th>
                    <th>Trạng thái</th>
                    <th>Cập nhật</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>

                <tbody>
                  {visibleQuestions.map(question => (
                    <tr key={question.questionId}>
                      <td>
                        <strong>
                          {question.questionId.slice(0, 8)}
                          ...
                        </strong>

                        <small>
                          {question.questionId}
                        </small>
                      </td>

                      <td>
                        {question.partId ?? '—'}
                      </td>

                      <td>
                        {question.questionType || '—'}
                      </td>

                      <td>
                        <span
                          className={
                            `teacher-status-pill ${
                              question.status || ''
                            }`
                          }
                        >
                          {statusLabels[question.status] ||
                            question.status}
                        </span>
                      </td>

                      <td>
                        {formatDate(question.updatedAt)}
                      </td>

                      <td>
                        <button
                          type="button"
                          className="teacher-outline-button"
                          onClick={() =>
                            openDetail(question.questionId)
                          }
                        >
                          <Eye size={16} />
                          Xem
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        {/* PAGINATION */}
        {!loading && !error && (
          <div className="teacher-pagination">
            <span>
              Trang {totalPages ? page + 1 : 0}
              {' / '}
              {totalPages}
            </span>

            <div>
              <button
                type="button"
                aria-label="Trang trước"
                disabled={page === 0}
                onClick={() =>
                  setPage(value => value - 1)
                }
              >
                <ChevronLeft size={18} />
              </button>

              <button
                type="button"
                aria-label="Trang sau"
                disabled={page + 1 >= totalPages}
                onClick={() =>
                  setPage(value => value + 1)
                }
              >
                <ChevronRight size={18} />
              </button>
            </div>
          </div>
        )}
      </section>

      {/* DETAIL MODAL */}
      {detailOpen && (
        <div
          className="teacher-modal-backdrop"
          onMouseDown={event => {
            if (event.target === event.currentTarget) {
              closeDetail();
            }
          }}
        >
          <section
            className="teacher-question-detail"
            role="dialog"
            aria-modal="true"
            aria-label="Chi tiết câu hỏi"
          >
            {/* MODAL HEADER */}
            <div className="teacher-question-detail-header">
              <h2>Chi tiết câu hỏi</h2>

              <button
                type="button"
                aria-label="Đóng"
                onClick={closeDetail}
              >
                <X size={20} />
              </button>
            </div>

            {/* DETAIL LOADING */}
            {detailLoading && (
              <p>Đang tải chi tiết câu hỏi...</p>
            )}

            {/* DETAIL ERROR */}
            {detailError && (
              <div
                className="teacher-editor-error"
                role="alert"
              >
                <AlertCircle size={18} />
                {detailError}
              </div>
            )}

            {/* DETAIL CONTENT */}
            {selected && (
              <>
                <p>
                  <strong>Mã câu hỏi:</strong>{' '}
                  {selected.question?.questionId}
                </p>

                <p>
                  <strong>Trạng thái:</strong>{' '}
                  {statusLabels[
                    selected.question?.status
                  ] || selected.question?.status}
                </p>

                <p>
                  <strong>Loại câu hỏi:</strong>{' '}
                  {selected.question?.questionType || '—'}
                </p>

                {/* VERSIONS */}
                {[...(selected.versions ?? [])]
                  .sort(
                    (a, b) =>
                      b.versionNumber - a.versionNumber
                  )
                  .map(version => (
                    <article
                      key={version.questionVersionId}
                      className="teacher-version-card"
                    >
                      <h3>
                        Phiên bản {version.versionNumber}
                        {' — '}
                        {statusLabels[version.status] ||
                          version.status}
                      </h3>

                      <p>
                        <strong>Độ khó:</strong>{' '}
                        {version.difficulty || '—'}
                      </p>

                      <p>
                        <strong>Câu hỏi:</strong>
                      </p>

                      <p>
                        {version.questionText}
                      </p>

                      {/* ANSWERS */}
                      <ol
                        style={{
                          listStyle: 'none',
                          paddingLeft: 0,
                        }}
                      >
                        {(version.choices ?? []).map(
                          (choice, index) => (
                            <li
                              key={
                                choice.choiceId ??
                                `${choice.label}-${index}`
                              }
                              style={{
                                padding: '7px 0',
                                color: choice.isCorrect
                                  ? '#148653'
                                  : 'inherit',
                                fontWeight: choice.isCorrect
                                  ? 600
                                  : 400,
                              }}
                            >
                              {choice.label}.{' '}
                              {choice.content}
                              {choice.isCorrect
                                ? ' ✓ Đáp án đúng'
                                : ''}
                            </li>
                          )
                        )}
                      </ol>

                      {/* EXPLANATION */}
                      {version.explanation && (
                        <p>
                          <strong>Giải thích:</strong>{' '}
                          {version.explanation}
                        </p>
                      )}
                    </article>
                  ))}
              </>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
