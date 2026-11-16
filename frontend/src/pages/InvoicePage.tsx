import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, post, put } from '../api/client';
import type { Invoice } from '../api/types';
import { addDays, fmtDate, fmtMoney, today } from '../format';
import { ErrorText, Loading, PageHeader, StatusBadge } from '../components/ui';
import { useAccounts } from './AccountsPage';
import { useCustomers } from './CustomersPage';

interface Line { description: string; quantity: string; rate: string; accountId: string }

export default function InvoicePage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const inv = useQuery({ queryKey: ['invoice', id], queryFn: () => api<Invoice>(`/invoices/${id}`), enabled: !isNew });
  if (!isNew && !inv.data) return <Loading />;
  if (isNew || inv.data!.status === 'DRAFT') return <InvoiceEditor key={id} invoice={inv.data} />;
  return <InvoiceView invoice={inv.data!} />;
}

function InvoiceEditor({ invoice }: { invoice?: Invoice }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const customers = useCustomers().data ?? [];
  const income = (useAccounts().data ?? []).filter((a) => a.type === 'INCOME' && a.active);
  const [customerId, setCustomerId] = useState(invoice?.customerId ?? '');
  const [invoiceDate, setInvoiceDate] = useState(invoice?.invoiceDate ?? today());
  const [dueDate, setDueDate] = useState(invoice?.dueDate ?? addDays(today(), 30));
  const [memo, setMemo] = useState(invoice?.memo ?? '');
  const [lines, setLines] = useState<Line[]>(
    invoice?.lines.map((l) => ({ description: l.description, quantity: String(l.quantity), rate: String(l.rate), accountId: l.accountId }))
      ?? [{ description: '', quantity: '1', rate: '', accountId: '' }],
  );
  useEffect(() => {
    if (income.length && lines.some((l) => !l.accountId)) {
      setLines((ls) => ls.map((l) => (l.accountId ? l : { ...l, accountId: income[0].id })));
    }
  }, [income.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const total = lines.reduce((s, l) => s + (Number(l.quantity) || 0) * (Number(l.rate) || 0), 0);
  const set = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const body = () => ({
    customerId, invoiceDate, dueDate, memo,
    lines: lines.filter((l) => l.description.trim()).map((l) => ({
      description: l.description, quantity: Number(l.quantity) || 0, rate: Number(l.rate) || 0, accountId: l.accountId || null,
    })),
  });

  const save = useMutation({
    mutationFn: async (approve: boolean) => {
      let saved = invoice ? await put<Invoice>(`/invoices/${invoice.id}`, body()) : await post<Invoice>('/invoices', body());
      if (approve) saved = await post<Invoice>(`/invoices/${saved.id}/approve`);
      return saved;
    },
    onSuccess: (saved) => {
      qc.invalidateQueries();
      navigate(`/invoices/${saved.id}`, { replace: true });
    },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate(false); };

  return (
    <>
      <PageHeader title={invoice ? `Invoice #${invoice.number} (draft)` : 'New invoice'} />
      <form className="card form" onSubmit={submit}>
        <div className="row">
          <label className="grow">Customer
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
              <option value="">Choose a customer…</option>
              {customers.map((c) => <option key={c.id} value={c.id}>{c.displayName}</option>)}
            </select>
          </label>
          <label>Invoice date<input type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} required /></label>
          <label>Due date<input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required /></label>
        </div>
        <table className="table lines">
          <thead><tr><th>Description</th><th>Income account</th><th className="num">Qty</th><th className="num">Rate</th><th className="num">Amount</th><th /></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td><input value={l.description} onChange={(e) => set(i, { description: e.target.value })} placeholder="Product or service" /></td>
                <td>
                  <select value={l.accountId} onChange={(e) => set(i, { accountId: e.target.value })}>
                    {income.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                  </select>
                </td>
                <td><input className="num" type="number" step="0.01" min="0" value={l.quantity} onChange={(e) => set(i, { quantity: e.target.value })} /></td>
                <td><input className="num" type="number" step="0.01" min="0" value={l.rate} onChange={(e) => set(i, { rate: e.target.value })} /></td>
                <td className="num">{fmtMoney((Number(l.quantity) || 0) * (Number(l.rate) || 0))}</td>
                <td>{lines.length > 1 && <button type="button" className="btn link" onClick={() => setLines(lines.filter((_, j) => j !== i))}>✕</button>}</td>
              </tr>
            ))}
            <tr className="totals">
              <td colSpan={2}><button type="button" className="btn small" onClick={() => setLines([...lines, { description: '', quantity: '1', rate: '', accountId: income[0]?.id ?? '' }])}>+ Add line</button></td>
              <td colSpan={2} className="num">Total</td>
              <td className="num strong">{fmtMoney(total)}</td>
              <td />
            </tr>
          </tbody>
        </table>
        <label>Message on invoice<textarea rows={2} value={memo} onChange={(e) => setMemo(e.target.value)} /></label>
        <ErrorText error={save.error} />
        <div className="form-actions">
          <button type="button" className="btn" onClick={() => navigate('/invoices')}>Cancel</button>
          <button className="btn" disabled={save.isPending}>Save draft</button>
          <button type="button" className="btn primary" disabled={save.isPending || total <= 0} onClick={() => save.mutate(true)}>Save and approve</button>
        </div>
      </form>
    </>
  );
}

function InvoiceView({ invoice }: { invoice: Invoice }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const banks = (useAccounts().data ?? []).filter((a) => a.subtype === 'BANK');
  const [amount, setAmount] = useState(String(invoice.balanceDue));
  const [date, setDate] = useState(today());
  const [depositAccountId, setDeposit] = useState('');
  const [method, setMethod] = useState('ACH');
  const refresh = () => qc.invalidateQueries();

  const pay = useMutation({
    mutationFn: () => post<Invoice>(`/invoices/${invoice.id}/payments`, {
      amount: Number(amount), date, depositAccountId: depositAccountId || null, method,
    }),
    onSuccess: (inv) => { setAmount(String(inv.balanceDue)); refresh(); },
  });
  const voidIt = useMutation({ mutationFn: () => post(`/invoices/${invoice.id}/void`), onSuccess: refresh });
  const canPay = invoice.status === 'OPEN' || invoice.status === 'PARTIALLY_PAID';

  return (
    <>
      <PageHeader title={`Invoice #${invoice.number}`}>
        <StatusBadge status={invoice.status} overdue={invoice.overdue} />
        {canPay && invoice.amountPaid === 0 && (
          <button className="btn" onClick={() => confirm('Void this invoice? A reversing journal entry will be posted.') && voidIt.mutate()}>Void</button>
        )}
        <button className="btn" onClick={() => navigate('/invoices')}>Back</button>
      </PageHeader>
      <ErrorText error={voidIt.error} />
      <div className="grid-2 wide-left">
        <section className="card">
          <div className="doc-head">
            <div><div className="muted small">Bill to</div><strong>{invoice.customerName}</strong></div>
            <div className="right">
              <div><span className="muted">Date</span> {fmtDate(invoice.invoiceDate)}</div>
              <div><span className="muted">Due</span> {fmtDate(invoice.dueDate)}</div>
            </div>
          </div>
          <table className="table">
            <thead><tr><th>Description</th><th className="num">Qty</th><th className="num">Rate</th><th className="num">Amount</th></tr></thead>
            <tbody>
              {invoice.lines.map((l, i) => (
                <tr key={i}><td>{l.description}</td><td className="num">{l.quantity}</td><td className="num">{fmtMoney(l.rate)}</td><td className="num">{fmtMoney(l.amount)}</td></tr>
              ))}
              <tr className="totals"><td colSpan={3} className="num">Total</td><td className="num strong">{fmtMoney(invoice.total)}</td></tr>
              <tr><td colSpan={3} className="num muted">Paid</td><td className="num">{fmtMoney(invoice.amountPaid)}</td></tr>
              <tr><td colSpan={3} className="num">Balance due</td><td className="num strong">{fmtMoney(invoice.balanceDue)}</td></tr>
            </tbody>
          </table>
          {invoice.memo && <p className="muted">{invoice.memo}</p>}
        </section>
        <section className="card">
          <h2>Payments</h2>
          {invoice.payments.length === 0 && <p className="muted">No payments yet.</p>}
          {invoice.payments.map((p) => (
            <div key={p.id} className="pay-row"><span>{fmtDate(p.date)} · {p.method}</span><strong>{fmtMoney(p.amount)}</strong></div>
          ))}
          {canPay && (
            <form className="form" onSubmit={(e) => { e.preventDefault(); pay.mutate(); }}>
              <h3>Receive payment</h3>
              <label>Amount<input className="num" type="number" step="0.01" min="0.01" max={invoice.balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
              <label>Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></label>
              <label>Deposit to
                <select value={depositAccountId} onChange={(e) => setDeposit(e.target.value)}>
                  <option value="">Default bank account</option>
                  {banks.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                </select>
              </label>
              <label>Method
                <select value={method} onChange={(e) => setMethod(e.target.value)}>
                  {['ACH', 'Check', 'Card', 'Cash', 'Wire'].map((m) => <option key={m}>{m}</option>)}
                </select>
              </label>
              <ErrorText error={pay.error} />
              <button className="btn primary wide" disabled={pay.isPending}>Record payment</button>
            </form>
          )}
        </section>
      </div>
    </>
  );
}
