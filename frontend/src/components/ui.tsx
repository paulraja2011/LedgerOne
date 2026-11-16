import type { ReactNode } from 'react';
import { statusLabel } from '../format';

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="btn link" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function StatusBadge({ status, overdue }: { status: string; overdue?: boolean }) {
  const cls = overdue ? 'overdue' : status.toLowerCase();
  return <span className={`badge ${cls}`}>{overdue ? 'Overdue' : statusLabel(status)}</span>;
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="error">{error instanceof Error ? error.message : String(error)}</div>;
}

export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="page-header">
      <h1>{title}</h1>
      <div className="actions">{children}</div>
    </div>
  );
}

export function Loading() {
  return <div className="muted">Loading…</div>;
}
