import { Link, useLocation } from 'react-router-dom';
import { Home, BookOpen, Layers, Gamepad2, Settings } from 'lucide-react';

export function MobileBottomNav() {
  const location = useLocation();

  const navItems = [
    {
      id: 'home',
      label: 'Hôm nay',
      to: '/',
      icon: Home,
      isActive: (path) => path === '/' || path === '/dashboard',
    },
    {
      id: 'catalog',
      label: 'Học tập',
      to: '/catalog',
      icon: BookOpen,
      isActive: (path) => path.startsWith('/catalog') || path.startsWith('/listening'),
    },
    {
      id: 'vocabulary',
      label: 'Từ vựng',
      to: '/vocabulary',
      icon: Layers,
      isActive: (path) => path.startsWith('/vocabulary'),
    },
    {
      id: 'games',
      label: 'Game',
      to: '/games',
      icon: Gamepad2,
      isActive: (path) => path.startsWith('/games'),
    },
    {
      id: 'settings',
      label: 'Cài đặt',
      to: '/settings',
      icon: Settings,
      isActive: (path) => path.startsWith('/settings') || path.startsWith('/profile'),
    },
  ];

  return (
    <nav
      aria-label="Điều hướng di động"
      className="mobile-bottom-nav"
    >
      <div className="mobile-bottom-nav-grid">
        {navItems.map((item) => {
          const Icon = item.icon;
          const active = item.isActive(location.pathname);

          return (
            <Link
              key={item.id}
              to={item.to}
              aria-current={active ? 'page' : undefined}
              className={`mobile-bottom-nav-link ${
                active ? 'text-blue-600 font-bold' : 'text-gray-400 hover:text-gray-600 font-medium'
              }`}
            >
              <Icon size={20} className={active ? 'stroke-[2.5]' : 'stroke-[1.75]'} />
              <span className="text-[10px] mt-0.5 tracking-tight">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
