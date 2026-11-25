import { MOCK_ACTIVE } from '../../config/env';

/** Shown on every page while the development mock is serving responses. Renders nothing otherwise. */
export function MockBanner() {
  if (!MOCK_ACTIVE) return null;
  return (
    <div className="mock-banner" role="status">
      Development mock mode: all data is synthetic and no real backend or model is being called.
    </div>
  );
}
