import type { ReactNode } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { ErrorBoundary } from './components/feedback/ErrorBoundary';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { PlaygroundPage } from './features/playground/PlaygroundPage';
import { ProviderStatusPage } from './features/provider/ProviderStatusPage';
import { StructuredPage } from './features/structured/StructuredPage';
import { UsagePage } from './features/usage/UsagePage';
import { EmptyState } from './components/feedback/EmptyState';

/** All AI pages inside the console shell. The host supplies the Router (use `basename` to mount under a prefix). */
export function App({ headerActions }: { headerActions?: ReactNode }) {
  const { pathname } = useLocation();
  return (
    <AppShell headerActions={headerActions}>
      {/* Keyed by route so a crashed page recovers when the user navigates elsewhere. */}
      <ErrorBoundary key={pathname}>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/playground" element={<PlaygroundPage />} />
          <Route path="/structured" element={<StructuredPage />} />
          <Route path="/provider" element={<ProviderStatusPage />} />
          <Route path="/usage" element={<UsagePage />} />
          <Route path="*" element={<EmptyState title="Page not found">Use the navigation to open one of the AI pages.</EmptyState>} />
        </Routes>
      </ErrorBoundary>
    </AppShell>
  );
}
