import { useAuth } from '../auth/AuthProvider';

/** Shows who is signed in and offers sign-out. Rendered in the header of the hosted console. */
export function UserMenu() {
  const { user, logout } = useAuth();
  if (user === null) return null;
  return (
    <>
      <span className="muted user-name">Signed in as {user.username}</span>
      <button type="button" className="button button--secondary" onClick={() => void logout().catch(() => undefined)}>
        Sign out
      </button>
    </>
  );
}
