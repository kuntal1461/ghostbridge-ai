interface StatusBadgeProps {
  tone: 'good' | 'warn' | 'neutral';
  children: string;
}

/** Colour is never the only signal: the label text always carries the meaning. */
export function StatusBadge({ tone, children }: StatusBadgeProps) {
  return <span className={`badge badge--${tone}`}>{children}</span>;
}
