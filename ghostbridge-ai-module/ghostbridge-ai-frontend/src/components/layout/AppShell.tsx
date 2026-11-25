import { useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { MockBanner } from '../feedback/MockBanner';
import { NAV_ITEMS } from './navigation';

interface AppShellProps {
  children: ReactNode;
  /** Optional host content (for example the signed-in user) shown in the header. */
  headerActions?: ReactNode;
}

export function AppShell({ children, headerActions }: AppShellProps) {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const firstRender = useRef(true);

  // Route change: update the document title and move focus to <main> so keyboard and screen-reader users land on the new page.
  useEffect(() => {
    const current = NAV_ITEMS.find((item) => (item.end ? pathname === item.to : pathname.startsWith(item.to)));
    document.title = current ? `${current.label} · GhostBridge AI` : 'GhostBridge AI';
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.focus();
  }, [pathname]);
  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to main content
      </a>
      <header className="shell__header">
        <span className="brand">
          <span className="brand__mark" aria-hidden="true" />
          GhostBridge <span className="brand__sub">AI Console</span>
        </span>
        {headerActions ? <div className="shell__actions">{headerActions}</div> : null}
        <button
          type="button"
          className="button button--secondary nav-toggle"
          aria-expanded={open}
          aria-controls="primary-nav"
          onClick={() => setOpen((value) => !value)}
        >
          {open ? 'Close menu' : 'Menu'}
        </button>
      </header>
      <nav id="primary-nav" className={`nav${open ? ' nav--open' : ''}`} aria-label="Primary">
        <ul>
          {NAV_ITEMS.map((item) => (
            <li key={item.to}>
              <NavLink to={item.to} end={item.end} className="nav__link" onClick={() => setOpen(false)}>
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main id="main" ref={mainRef} className="shell__main" tabIndex={-1}>
        <MockBanner />
        {children}
      </main>
    </div>
  );
}
