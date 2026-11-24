
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BookOpen,
  Search,
  RefreshCw,
  Plus,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
} from 'lucide-react';
import { teacherContentApi } from '../../features/teacher/teacherContent.api';

const categories = [
  { value: '', label: 'Tất cả danh mục' },
  { value: 'GRAMMAR', label: 'Ngữ pháp' },
  { value: 'VOCAB', label: 'Từ vựng' },
  { value: 'LISTENING', label: 'Luyện nghe' },
  { value: 'READING', label: 'Luyện đọc' },
  { value: 'STRATEGIES', label: 'Chiến lược TOEIC' },
  { value: 'DOCS', label: 'Tài liệu' },
];

const statuses = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'DRAFT', label: 'Bản nháp' },
  { value: 'PUBLISHED', label: 'Đã xuất bản' },
  { value: 'ARCHIVED', label: 'Đã lưu trữ' },
];

const statusLabels = {
  DRAFT: 'Bản nháp',
  PUBLISHED: 'Đã xuất bản',
  ARCHIVED: 'Đã lưu trữ',
};

function formatDate(value) {
  if (!value) return '—';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';

  return date.toLocaleDateString('vi-VN');
}

export default function TeacherContentsPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [status, setStatus] = useState('');
  const [categoryCode, setCategoryCode] = useState('');
  const [search, setSearch] = useState('');
  const [revision, setRevision] = useState(0);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();

    async function loadContents() {
      setLoading(true);
      setError('');

      try {
        const data = await teacherContentApi.list({
          page,
          size: 20,
          status,
          categoryCode,
        });

        if (controller.signal.aborted) return;

        setItems(data.content ?? []);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      } catch (err) {
        if (controller.signal.aborted) return;

        setItems([]);
        setTotalPages(0);
        setTotalElements(0);
        setError(
          err.message || 'Không tải được danh sách học liệu.'
        );
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }

    loadContents();

    return () => controller.abort();
  }, [page, status, categoryCode, revision]);

  const visibleItems = items.filter(item =>
    (item.title ?? '')
      .toLocaleLowerCase('vi')
      .includes(search.trim().toLocaleLowerCase('vi'))
  );

  return (
    <div className="teacher-contents-page">
      <div className="teacher-page-heading teacher-page-toolbar">
        <div>
          <h1>Kho học liệu</h1>
          <p>
            Quản lý học liệu và các bản nháp do bạn biên soạn.
          </p>
        </div>

        <Link
          to="/teacher/content?kind=contents"
          className="teacher-primary-button"
        >
          <Plus size={18} />
          Tạo học liệu
        </Link>
      </div>

      <section className="teacher-content-panel">
        <div className="teacher-filter-bar">
          <label className="teacher-search-field">
            <Search size={18} />
            <input
              type="search"
              placeholder="Tìm trong trang hiện tại..."
              aria-label="Tìm học liệu trong trang hiện tại"
              value={search}
              onChange={event => setSearch(event.target.value)}
            />
          </label>

          <select
            aria-label="Lọc theo danh mục"
            value={categoryCode}
            onChange={event => {
              setCategoryCode(event.target.value);
              setPage(0);
            }}
          >
            {categories.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <select
            aria-label="Lọc theo trạng thái"
            value={status}
            onChange={event => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            {statuses.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <button
            type="button"
            className="teacher-outline-button"
            title="Tải lại danh sách"
            aria-label="Tải lại danh sách"
            onClick={() => setRevision(value => value + 1)}
          >
            <RefreshCw size={18} />
          </button>
        </div>

        <div className="teacher-list-summary">
          <strong>Danh sách học liệu</strong>
          <span>{totalElements} mục theo bộ lọc</span>
        </div>

        {loading && (
          <div className="teacher-empty-state" role="status">
            Đang tải học liệu từ Backend...
          </div>
        )}

        {!loading && error && (
          <div className="teacher-error-state" role="alert">
            <AlertCircle size={20} />
            <span>{error}</span>
          </div>
        )}

        {!loading && !error && visibleItems.length === 0 && (
          <div className="teacher-empty-state">
            <BookOpen size={34} />
            <h2>Chưa có học liệu phù hợp</h2>
            <p>
              Thử thay đổi bộ lọc hoặc kiểm tra học liệu
              thuộc tài khoản giảng viên.
            </p>
          </div>
        )}

        {!loading && !error && visibleItems.length > 0 && (
          <div className="teacher-table-scroll">
            <table className="teacher-data-table">
              <thead>
                <tr>
                  <th>Học liệu</th>
                  <th>Danh mục</th>
                  <th>Trạng thái</th>
                  <th>Độ khó</th>
                  <th>Cập nhật</th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map(item => (
                  <tr key={item.contentId}>
                    <td>
                      <strong>{item.title || 'Chưa có tiêu đề'}</strong>
                      <small>ID: {item.contentId}</small>
                    </td>
                    <td>{item.category?.name || '—'}</td>
                    <td>
                      <span
                        className={`teacher-status-pill ${
                          item.status || ''
                        }`}
                      >
                        {statusLabels[item.status] || item.status}
                      </span>
                    </td>
                    <td>{item.difficulty || '—'}</td>
                    <td>{formatDate(item.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!loading && !error && (
          <div className="teacher-pagination">
            <span>
              Trang {totalPages === 0 ? 0 : page + 1}
              {' / '}
              {totalPages}
            </span>

            <div>
              <button
                type="button"
                aria-label="Trang trước"
                disabled={page === 0}
                onClick={() => setPage(value => value - 1)}
              >
                <ChevronLeft size={18} />
              </button>

              <button
                type="button"
                aria-label="Trang sau"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(value => value + 1)}
              >
                <ChevronRight size={18} />
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
