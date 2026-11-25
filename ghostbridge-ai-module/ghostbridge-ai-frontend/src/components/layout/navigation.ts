export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

/** Every entry maps to a working page. Do not add decorative links. */
export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/playground', label: 'AI Playground' },
  { to: '/structured', label: 'Structured Output' },
  { to: '/provider', label: 'Provider Status' },
  { to: '/usage', label: 'Usage & Limits' },
];
