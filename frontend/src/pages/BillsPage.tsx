import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, post } from '../api/client';
import type { Bill } from '../api/types';
import { addDays, fmtDate, fmtMoney, today } from '../format';
import { ErrorText, Loading, Modal, PageHeader, StatusBadge } from '../components/ui';
import { useAccounts } from './AccountsPage';
import { useVendors } from './VendorsPage';

export default function BillsPage() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['bills'], queryFn: () => api<Bill[]>('/bills') });
  const [adding, setAdding] = useState(false);
  const [paying, setPaying] = useState<Bill | null>(null);
  const action = useMutation({
    mutationFn: ({ id, op }: { id: string; op: 'approve' | 'void' }) => post(`/bills/${id}/${op}`),
    onSuccess: () => qc.invalidateQueries(),
  });
  if (!data) return <Loading />;
  const open = data.filter((b) => b.status === 'OPEN' || b.status === 'PARTIALLY_PAID').reduce((s, b) => s + b.balanceDue, 0);
  return (
    <>
      <PageHeader title="Bills">
        <span className="muted">Unpaid <strong>{fmtMoney(open)}</strong></span>
        <button className="btn primary" onClick={() => setAdding(true)}>New bill</button>
      </PageHeader>
      <ErrorText error={action.error} />
      <div className="card flush">
        <table className="table">
          <thead><tr><th>Vendor</th><th>Ref</th><th>Bill date</th><th>Due</th><th className="num">Total</th><th className="num">Balance</th><th>Status</th><th /></tr></thead>
          <tbody>
            {data.map((b) => (
              <tr key={b.id}>
                <td>{b.vendorName}</td>
                <td className="muted">{b.reference}</td>
                <td>{fmtDate(b.billDate)}</td>
                <td>{fmtDate(b.dueDate)}</td>
                <td className="num">{fmtMoney(b.total)}</td>
                <td className="num">{fmtMoney(b.balanceDue)}</td>
                <td><StatusBadge status={b.status} overdue={b.overdue} /></td>
                <td className="num nowrap">
                  {b.status === 'DRAFT' && <button className="btn small" onClick={() => action.mutate({ id: b.id, op: 'approve' })}>Approve</button>}
                  {(b.status === 'OPEN' || b.status === 'PARTIALLY_PAID') && <button className="btn small primary" onClick={() => setPaying(b)}>Pay</button>}
                  {(b.status === 'DRAFT' || (b.status === 'OPEN' && b.amountPaid === 0)) && (
                    <button className="btn small" onClick={() => confirm('Void this bill?') && action.mutate({ id: b.id, op: 'void' })}>Void</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {adding && <NewBill onClose={() => setAdding(false)} />}
      {paying && <PayBill bill={paying} onClose={() => setPaying(null)} />}
    </>
  );
}

interface Line { description: string; amount: string; accountId: string }

function NewBill({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const vendors = useVendors().data ?? [];
  const expense = (useAccounts().data ?? []).filter((a) => a.type === 'EXPENSE' || a.subtype === 'FIXED_ASSET');
  const [vendorId, setVendorId] = useState('');
  const [reference, setReference] = useState('');
  const [billDate, setBillDate] = useState(today());
  const [dueDate, setDueDate] = useState(addDays(today(), 30));
  const [lines, setLines] = useState<Line[]>([{ description: '', amount: '', accountId: '' }]);
  const total = lines.reduce((s, l) => s + (Number(l.amount) || 0), 0);
  const set = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));

  const m = useMutation({
    mutationFn: async (approve: boolean) => {
      const b = await post<Bill>('/bills', {
        vendorId, reference, billDate, dueDate,
        lines: lines.filter((l) => l.accountId && Number(l.amount) > 0)
          .map((l) => ({ description: l.description, amount: Number(l.amount), accountId: l.accountId })),
      });
      if (approve) await post(`/bills/${b.id}/approve`);
    },
    onSuccess: () => { qc.invalidateQueries(); onClose(); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); m.mutate(true); };

  return (
    <Modal title="New bill" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="row">
          <label className="grow">Vendor
            <select value={vendorId} onChange={(e) => setVendorId(e.target.value)} required>
              <option value="">Choose a vendor…</option>
              {vendors.map((v) => <option key={v.id} value={v.id}>{v.displayName}</option>)}
            </select>
          </label>
          <label>Bill no.<input value={reference} onChange={(e) => setReference(e.target.value)} /></label>
        </div>
        <div className="row">
          <label>Bill date<input type="date" value={billDate} onChange={(e) => setBillDate(e.target.value)} required /></label>
          <label>Due date<input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required /></label>
        </div>
        <table className="table lines">
          <thead><tr><th>Category</th><th>Description</th><th className="num">Amount</th></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td>
                  <select value={l.accountId} onChange={(e) => set(i, { accountId: e.target.value })} required>
                    <option value="">Category…</option>
                    {expense.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                  </select>
                </td>
                <td><input value={l.description} onChange={(e) => set(i, { description: e.target.value })} /></td>
                <td><input className="num" type="number" step="0.01" min="0" value={l.amount} onChange={(e) => set(i, { amount: e.target.value })} required /></td>
              </tr>
            ))}
            <tr className="totals">
              <td><button type="button" className="btn small" onClick={() => setLines([...lines, { description: '', amount: '', accountId: '' }])}>+ Add line</button></td>
              <td className="num">Total</td>
              <td className="num strong">{fmtMoney(total)}</td>
            </tr>
          </tbody>
        </table>
        <ErrorText error={m.error} />
        <div className="form-actions">
          <button type="button" className="btn" disabled={m.isPending} onClick={() => m.mutate(false)}>Save draft</button>
          <button className="btn primary" disabled={m.isPending || total <= 0}>Save and approve</button>
        </div>
      </form>
    </Modal>
  );
}

function PayBill({ bill, onClose }: { bill: Bill; onClose: () => void }) {
  const qc = useQueryClient();
  const payFrom = (useAccounts().data ?? []).filter((a) => a.subtype === 'BANK' || a.subtype === 'CREDIT_CARD');
  const [amount, setAmount] = useState(String(bill.balanceDue));
  const [date, setDate] = useState(today());
  const [bankAccountId, setBank] = useState('');
  const m = useMutation({
    mutationFn: () => post(`/bills/${bill.id}/pay`, { amount: Number(amount), date, bankAccountId: bankAccountId || null }),
    onSuccess: () => { qc.invalidateQueries(); onClose(); },
  });
  return (
    <Modal title={`Pay ${bill.vendorName}`} onClose={onClose}>
      <form className="form" onSubmit={(e) => { e.preventDefault(); m.mutate(); }}>
        <p className="muted">Balance due {fmtMoney(bill.balanceDue)}</p>
        <div className="row">
          <label>Amount<input className="num" type="number" step="0.01" min="0.01" max={bill.balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
          <label>Payment date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></label>
        </div>
        <label>Pay from
          <select value={bankAccountId} onChange={(e) => setBank(e.target.value)}>
            <option value="">Default bank account</option>
            {payFrom.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </label>
        <ErrorText error={m.error} />
        <div className="form-actions"><button className="btn primary" disabled={m.isPending}>Record payment</button></div>
      </form>
    </Modal>
  );
}
