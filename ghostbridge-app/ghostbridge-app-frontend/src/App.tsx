import { AiConsole } from 'ghostbridge-ai-frontend';
import { Route, Routes } from 'react-router-dom';
import { RequireAuth } from './components/RequireAuth';
import { UserMenu } from './components/UserMenu';
import { LoginPage } from './pages/LoginPage';

/**
 * The main GhostBridge application. It owns sign-in and routing and hosts the existing AI console (all AI pages and their
 * navigation) behind authentication. Every request goes to GhostBridge App on the same origin.
 */
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <AiConsole headerActions={<UserMenu />} />
          </RequireAuth>
        }
      />
    </Routes>
  );
}
