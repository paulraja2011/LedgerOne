import { Fragment, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, post } from '../api/client';
import type { JournalEntry, Page } from '../api/types';
import { fmtDate, fmtMoney, today } from '../format';
import { ErrorText, Loading, Modal, PageHeader } from '../components/ui';
import { useAccounts } from './AccountsPage';

export default function JournalPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const { data } = useQuery({
    queryKey: ['journal', page],
    queryFn: () => api<Page<JournalEntry>>(`/journal-entries?page=${page}&size=25`),
  });
  const reverse = useMutation({
    mutationFn: (id: string) => post<JournalEntry>(`/journal-entries/${id}/reverse`),
    onSuccess: () => qc.invalidateQueries(),
  });
  if (!data) return <Loading />;
  return (
    <>
      <PageHeader title="Journal entries">
        <button className="btn primary" onClick={() => setAdding(true)}>New journal entry</button>
      </PageHeader>
      <ErrorText error={reverse.error} />
      <div className="card flush">
        <table className="table">
          <thead><tr><th>#</th><th>Date</th><th>Source</th><th>Memo</th><th className="num">Amount</th><th /></tr></thead>
          <tbody>
            {data.content.map((e) => (
              <Fragment key={e.id}>
                <tr className="clickable" onClick={() => setOpen(open === e.id ? null : e.id)}>
                  <td>{e.number}</td>
                  <td>{fmtDate(e.date)}</td>
                  <td className="muted">{e.sourceType.toLowerCase().replace('_', ' ')}</td>
                  <td>{e.memo}{e.reversed && <span className="tag">reversed</span>}{e.reversalOfId && <span className="tag">reversal</span>}</td>
                  <td className="num">{fmtMoney(e.total)}</td>
                  <td className="num">
                    {e.sourceType === 'MANUAL' && !e.reversed && !e.reversalOfId && (
                      <button className="btn small" onClick={(ev) => { ev.stopPropagation(); if (confirm(`Reverse entry #${e.number}?`)) reverse.mutate(e.id); }}>Reverse</button>
                    )}
                  </td>
                </tr>
                {open === e.id && (
                  <tr className="detail-row">
                    <td colSpan={6}>
                      <table className="table inner">
                        <tbody>
                          {e.lines.map((l, i) => (
                            <tr key={i}>
                              <td>{l.accountCode} · {l.accountName}</td>
                              <td className="muted">{l.description}</td>
                              <td className="num">{l.debit > 0 ? fmtMoney(l.debit) : ''}</td>
                              <td className="num">{l.credit > 0 ? fmtMoney(l.credit) : ''}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
      <div className="pager">
        <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <span className="muted">Page {data.number + 1} of {Math.max(1, data.totalPages)}</span>
        <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
      </div>
      {adding && <NewEntry onClose={() => setAdding(false)} />}
    </>
  );
}

interface Line { accountId: string; debit: string; credit: string; description: string }
const blank = (): Line => ({ accountId: '', debit: '', credit: '', description: '' });

function NewEntry({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const accounts = useAccounts().data ?? [];
  const [date, setDate] = useState(today());
  const [memo, setMemo] = useState('');
  const [lines, setLines] = useState<Line[]>([blank(), blank()]);
  const dr = lines.reduce((s, l) => s + (Number(l.debit) || 0), 0);
  const cr = lines.reduce((s, l) => s + (Number(l.credit) || 0), 0);
  const balanced = Math.abs(dr - cr) < 0.005 && dr > 0;

  const m = useMutation({
    mutationFn: () => post('/journal-entries', {
      date, memo,
      lines: lines.filter((l) => l.accountId).map((l) => ({
        accountId: l.accountId, debit: Number(l.debit) || 0, credit: Number(l.credit) || 0, description: l.description,
      })),
    }),
    onSuccess: () => { qc.invalidateQueries(); onClose(); },
  });
  const set = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const submit = (e: FormEvent) => { e.preventDefault(); m.mutate(); };

  return (
    <Modal title="New journal entry" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="row">
          <label>Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></label>
          <label className="grow">Memo<input value={memo} onChange={(e) => setMemo(e.target.value)} /></label>
        </div>
        <table className="table lines">
          <thead><tr><th>Account</th><th>Description</th><th className="num">Debit</th><th className="num">Credit</th></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td>
                  <select value={l.accountId} onChange={(e) => set(i, { accountId: e.target.value })}>
                    <option value="">Choose account…</option>
                    {accounts.map((a) => <option key={a.id} value={a.id}>{a.code} · {a.name}</option>)}
                  </select>
                </td>
                <td><input value={l.description} onChange={(e) => set(i, { description: e.target.value })} /></td>
                <td><input className="num" type="number" step="0.01" min="0" value={l.debit} onChange={(e) => set(i, { debit: e.target.value, credit: '' })} /></td>
                <td><input className="num" type="number" step="0.01" min="0" value={l.credit} onChange={(e) => set(i, { credit: e.target.value, debit: '' })} /></td>
              </tr>
            ))}
            <tr className="totals">
              <td><button type="button" className="btn small" onClick={() => setLines([...lines, blank()])}>+ Add line</button></td>
              <td className="num">Totals</td>
              <td className="num">{fmtMoney(dr)}</td>
              <td className="num">{fmtMoney(cr)}</td>
            </tr>
          </tbody>
        </table>
        {!balanced && dr + cr > 0 && <div className="warn-text">Out of balance by {fmtMoney(Math.abs(dr - cr))}</div>}
        <ErrorText error={m.error} />
        <div className="form-actions"><button className="btn primary" disabled={!balanced || m.isPending}>Post entry</button></div>
      </form>
    </Modal>
  );
}
