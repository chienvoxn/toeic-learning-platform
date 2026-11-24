
import { useEffect, useRef, useState } from 'react';
import {
  Link,
  NavLink,
  Outlet,
  useNavigate,
} from 'react-router-dom';
import {
  LayoutDashboard,
  ClipboardList,
  BookOpen,
  ListChecks,
  Users,
  BarChart3,
  Bell,
  ChevronDown,
  ChevronLeft,
  Menu,
  X,
  UserRound,
  Settings,
} from 'lucide-react';

import { Brand } from '../atoms/Brand';
import { useAuth } from '../../features/auth/hooks/useAuth';
import '../../pages/teacher/teacher.css';

const navigation = [
  {
    label: 'Tổng quan',
    path: '/teacher',
    icon: LayoutDashboard,
  },
  {
    label: 'Đề thi & Bài nộp',
    path: '/teacher/exams',
    icon: ClipboardList,
  },
  {
    label: 'Kho học liệu',
    path: '/teacher/contents',
    icon: BookOpen,
  },
  {
    label: 'Ngân hàng đề thi',
    path: '/teacher/questions',
    icon: ListChecks,
  },
  {
    label: 'Lớp học & Học viên',
    path: '/teacher/classes',
    icon: Users,
  },
  {
    label: 'Báo cáo kết quả',
    path: '/teacher/reports',
    icon: BarChart3,
  },
];

export default function TeacherLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showLogout, setShowLogout] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const profileRef = useRef(null);
  const notificationRef = useRef(null);

  const displayName =
    user?.fullName ||
    user?.displayName ||
    user?.name ||
    user?.email ||
    'Giảng viên';

  const avatarUrl = user?.avatarUrl;
  const initials = displayName
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map(part => part.charAt(0))
    .join('')
    .toUpperCase();

  useEffect(() => {
    function handlePointerDown(event) {
      if (
        profileRef.current &&
        !profileRef.current.contains(event.target)
      ) {
        setShowProfileMenu(false);
      }

      if (
        notificationRef.current &&
        !notificationRef.current.contains(event.target)
      ) {
        setShowNotifications(false);
      }
    }

    function handleEscape(event) {
      if (event.key === 'Escape') {
        setShowProfileMenu(false);
        setShowNotifications(false);
        setMobileOpen(false);
      }
    }

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleEscape);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  function Avatar({ size = 'normal' }) {
    return (
      <span className={`teacher-avatar teacher-avatar-${size}`}>
        {avatarUrl ? (
          <img src={avatarUrl} alt="" />
        ) : (
          <span>{initials || 'GV'}</span>
        )}
      </span>
    );
  }

  async function handleLogout() {
    if (loggingOut) return;

    setLoggingOut(true);

    try {
      const result = await logout();

      navigate('/login', {
        replace: true,
        state: {
          logoutFailed: result?.revocationFailed,
        },
      });
    } finally {
      setLoggingOut(false);
    }
  }

  function toggleNotifications() {
    setShowNotifications(value => !value);
    setShowProfileMenu(false);
  }

  function toggleProfileMenu() {
    setShowProfileMenu(value => !value);
    setShowNotifications(false);
  }

  return (
    <div
      className={`teacher-app ${
        collapsed ? 'teacher-is-collapsed' : ''
      }`}
    >
      {/* Overlay for mobile */}
      {mobileOpen && (
        <button
          type="button"
          className="teacher-mobile-overlay"
          aria-label="Đóng menu"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`teacher-sidebar ${
          collapsed ? 'collapsed' : ''
        } ${mobileOpen ? 'is-open' : ''}`}
        aria-label="Menu giảng viên"
      >
        {/* Brand */}
        <div className="teacher-sidebar-brand">
          <Brand
            compact={collapsed}
            className="teacher-brand"
          />

          <button
            type="button"
            className="teacher-mobile-close"
            aria-label="Đóng menu"
            onClick={() => setMobileOpen(false)}
          >
            <X size={19} />
          </button>
        </div>

        {/* Main navigation */}
        <div className="teacher-sidebar-scroll">
          <nav
            className="teacher-nav"
            aria-label="Điều hướng giảng viên"
          >
            {navigation.map(item => {
              const Icon = item.icon;

              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/teacher'}
                  title={item.label}
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) =>
                    `teacher-nav-item ${
                      isActive ? 'active' : ''
                    }`
                  }
                >
                  <Icon size={19} aria-hidden="true" />

                  <span className="teacher-nav-label">
                    {item.label}
                  </span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* Footer like Learner Sidebar */}
        <div className="teacher-sidebar-footer">
          <Link
            to="/profile"
            className="teacher-sidebar-user"
            onClick={() => setMobileOpen(false)}
            title={displayName}
          >
            <Avatar />

            <div className="teacher-user-info">
              <strong>{displayName}</strong>
              <small>Giảng viên</small>
            </div>
          </Link>

          <button
            type="button"
            className="teacher-collapse"
            onClick={() => setCollapsed(value => !value)}
            aria-label={
              collapsed ? 'Mở rộng menu' : 'Thu gọn menu'
            }
            title={collapsed ? 'Mở rộng' : 'Thu gọn'}
          >
            <ChevronLeft
              size={15}
              className="teacher-collapse-chevron"
              aria-hidden="true"
            />

            <span>
              {collapsed ? 'Mở rộng' : 'Thu gọn'}
            </span>
          </button>
        </div>
      </aside>

      {/* Main area */}
      <div className="teacher-main">
        {/* Header */}
        <header className="teacher-header">
          <div className="teacher-header-left">
            <button
              type="button"
              className="teacher-mobile-menu"
              aria-label="Mở menu giảng viên"
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen(true)}
            >
              <Menu size={21} />
            </button>

            <span className="teacher-role">
              GIẢNG VIÊN
            </span>
          </div>

          <div className="teacher-header-actions">
            {/* Notification */}
            <div
              className="teacher-dropdown-wrapper"
              ref={notificationRef}
            >
              <button
                type="button"
                className="teacher-icon-button"
                aria-label="Thông báo"
                aria-expanded={showNotifications}
                onClick={toggleNotifications}
              >
                <Bell size={20} />
              </button>

              {showNotifications && (
                <div className="teacher-dropdown">
                  <h3>Thông báo</h3>
                  <p>
                    Chức năng thông báo đang được
                    tích hợp với Backend.
                  </p>
                </div>
              )}
            </div>

            {/* Profile */}
            <div
              className="teacher-dropdown-wrapper"
              ref={profileRef}
            >
              <button
                type="button"
                className="teacher-profile-trigger"
                aria-label="Menu tài khoản"
                aria-expanded={showProfileMenu}
                onClick={toggleProfileMenu}
              >
                <Avatar size="small" />
                <ChevronDown size={16} />
              </button>

              {showProfileMenu && (
                <div className="teacher-dropdown teacher-profile-menu">
                  <div className="teacher-menu-account">
                    <strong>{displayName}</strong>
                    <small>Giảng viên</small>
                  </div>

                  <div className="teacher-menu-divider" />

                  <Link
                    to="/profile"
                    onClick={() => setShowProfileMenu(false)}
                  >
                    <UserRound size={17} />
                    Hồ sơ cá nhân
                  </Link>

                  <Link
                    to="/settings"
                    onClick={() => setShowProfileMenu(false)}
                  >
                    <Settings size={17} />
                    Cài đặt
                  </Link>
                </div>
              )}
            </div>

            {/* Separate logout */}
            <button
              type="button"
              className="teacher-logout"
              onClick={() => {
                setShowNotifications(false);
                setShowProfileMenu(false);
                setShowLogout(true);
              }}
            >
              Đăng xuất
            </button>
          </div>
        </header>

        {/* Dynamic page */}
        <main className="teacher-content" id="teacher-main-content">
          <Outlet />
        </main>
      </div>

      {/* Logout confirmation */}
      {showLogout && (
        <div
          className="teacher-modal-backdrop"
          onMouseDown={event => {
            if (
              event.target === event.currentTarget &&
              !loggingOut
            ) {
              setShowLogout(false);
            }
          }}
        >
          <div
            className="teacher-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="teacher-logout-title"
          >
            <h2 id="teacher-logout-title">
              Xác nhận đăng xuất
            </h2>

            <p>
              Bạn có chắc chắn muốn đăng xuất
              khỏi ENGLINGO?
            </p>

            <div className="teacher-modal-actions">
              <button
                type="button"
                className="teacher-modal-cancel"
                disabled={loggingOut}
                onClick={() => setShowLogout(false)}
              >
                Hủy
              </button>

              <button
                type="button"
                className="teacher-modal-confirm"
                disabled={loggingOut}
                onClick={handleLogout}
              >
                {loggingOut
                  ? 'Đang đăng xuất...'
                  : 'Đăng xuất'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
