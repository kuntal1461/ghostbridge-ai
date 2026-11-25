interface SkeletonProps {
  /** Accessible description of what is loading. */
  label: string;
  lines?: number;
}

export function Skeleton({ label, lines = 3 }: SkeletonProps) {
  return (
    <div className="skeleton" role="status" aria-busy="true" aria-live="polite">
      <span className="visually-hidden">{label}</span>
      {Array.from({ length: lines }, (_, index) => (
        <div key={index} className="skeleton__line" aria-hidden="true" />
      ))}
    </div>
  );
}
