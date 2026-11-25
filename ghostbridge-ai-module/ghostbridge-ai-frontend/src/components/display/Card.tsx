import type { ReactNode } from 'react';

interface CardProps {
  title: string;
  titleId: string;
  children: ReactNode;
  note?: string;
}

export function Card({ title, titleId, children, note }: CardProps) {
  return (
    <section className="card" aria-labelledby={titleId}>
      <h2 id={titleId} className="card__title">
        {title}
      </h2>
      {children}
      {note ? <p className="card__note muted">{note}</p> : null}
    </section>
  );
}
