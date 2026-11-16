import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';

const nav = [
  { to: '/', label: 'Dashboard', end: true },
  { section: 'Sales' },
  { to: '/invoices', label: 'Invoices' },
  { to: '/customers', label: 'Customers' },
  { section: 'Expenses' },
  { to: '/bills', label: 'Bills' },
  { to: '/vendors', label: 'Vendors' },
  { section: 'Accounting' },
  { to: '/accounts', label: 'Chart of accounts' },
  { to: '/journal', label: 'Journal entries' },
  { to: '/reports', label: 'Reports' },
] as const;

export default function Layout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">L1</span> LedgerOne
        </div>
        <button className="btn primary new-btn" onClick={() => navigate('/invoices/new')}>+ New invoice</button>
        <nav>
          {nav.map((n, i) =>
            'section' in n ? (
              <div key={i} className="nav-section">{n.section}</div>
            ) : (
              <NavLink key={n.to} to={n.to} end={'end' in n} className={({ isActive }) => (isActive ? 'active' : '')}>
                {n.label}
              </NavLink>
            ),
          )}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <div className="company">{session?.company.name}</div>
          <div className="user">
            <span>{session?.user.name} · {session?.user.role.toLowerCase()}</span>
            <button className="btn link" onClick={logout}>Sign out</button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
