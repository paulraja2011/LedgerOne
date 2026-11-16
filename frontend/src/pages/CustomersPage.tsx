import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, post, put } from '../api/client';
import type { Customer } from '../api/types';
import { fmtMoney } from '../format';
import { ErrorText, Loading, Modal, PageHeader } from '../components/ui';

export const useCustomers = () => useQuery({ queryKey: ['customers'], queryFn: () => api<Customer[]>('/customers') });

export default function CustomersPage() {
  const { data } = useCustomers();
  const [editing, setEditing] = useState<Customer | 'new' | null>(null);
  if (!data) return <Loading />;
  return (
    <>
      <PageHeader title="Customers">
        <button className="btn primary" onClick={() => setEditing('new')}>New customer</button>
      </PageHeader>
      <div className="card flush">
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th className="num">Open balance</th><th className="num">Overdue</th><th /></tr></thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id}>
                <td><Link to={`/invoices?customer=${c.id}`}>{c.displayName}</Link></td>
                <td>{c.email}</td>
                <td>{c.phone}</td>
                <td className="num">{fmtMoney(c.openBalance)}</td>
                <td className={`num ${c.overdueBalance > 0 ? 'bad-text' : ''}`}>{fmtMoney(c.overdueBalance)}</td>
                <td className="num"><button className="btn small" onClick={() => setEditing(c)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && <ContactForm kind="customer" initial={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

export function ContactForm({ kind, initial, onClose }: {
  kind: 'customer' | 'vendor';
  initial?: { id: string; displayName: string; email?: string; phone?: string; billingAddress?: string; address?: string };
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [form, setForm] = useState({
    displayName: initial?.displayName ?? '', email: initial?.email ?? '', phone: initial?.phone ?? '',
    address: initial?.billingAddress ?? initial?.address ?? '',
  });
  const path = kind === 'customer' ? '/customers' : '/vendors';
  const body = kind === 'customer'
    ? { displayName: form.displayName, email: form.email, phone: form.phone, billingAddress: form.address }
    : { displayName: form.displayName, email: form.email, phone: form.phone, address: form.address };
  const m = useMutation({
    mutationFn: () => (initial ? put(`${path}/${initial.id}`, body) : post(path, body)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: [kind + 's'] }); onClose(); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); m.mutate(); };
  return (
    <Modal title={`${initial ? 'Edit' : 'New'} ${kind}`} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label>Display name<input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required autoFocus /></label>
        <div className="row">
          <label className="grow">Email<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <label className="grow">Phone<input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
        </div>
        <label>Address<textarea rows={2} value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
        <ErrorText error={m.error} />
        <div className="form-actions"><button className="btn primary" disabled={m.isPending}>Save</button></div>
      </form>
    </Modal>
  );
}
