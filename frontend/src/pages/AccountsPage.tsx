import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, post } from '../api/client';
import type { Account, AccountType } from '../api/types';
import { fmtMoney } from '../format';
import { ErrorText, Loading, Modal, PageHeader } from '../components/ui';

const TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'];
const SUBTYPES: Record<AccountType, string[]> = {
  ASSET: ['BANK', 'OTHER_CURRENT_ASSET', 'FIXED_ASSET'],
  LIABILITY: ['CREDIT_CARD', 'OTHER_CURRENT_LIABILITY', 'LONG_TERM_LIABILITY'],
  EQUITY: ['OWNER_EQUITY'],
  INCOME: ['SALES', 'SERVICE', 'OTHER_INCOME'],
  EXPENSE: ['OPERATING', 'COST_OF_GOODS_SOLD', 'OTHER_EXPENSE'],
};

export const useAccounts = () => useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') });

export default function AccountsPage() {
  const { data } = useAccounts();
  const [adding, setAdding] = useState(false);
  if (!data) return <Loading />;
  return (
    <>
      <PageHeader title="Chart of accounts">
        <button className="btn primary" onClick={() => setAdding(true)}>New account</button>
      </PageHeader>
      <div className="card flush">
        <table className="table">
          <thead><tr><th>Code</th><th>Name</th><th>Type</th><th>Detail type</th><th className="num">Balance</th></tr></thead>
          <tbody>
            {TYPES.map((t) => (
              <TypeGroup key={t} type={t} accounts={data.filter((a) => a.type === t)} />
            ))}
          </tbody>
        </table>
      </div>
      {adding && <NewAccount onClose={() => setAdding(false)} />}
    </>
  );
}

function TypeGroup({ type, accounts }: { type: AccountType; accounts: Account[] }) {
  return (
    <>
      <tr className="group-row"><td colSpan={5}>{type.charAt(0) + type.slice(1).toLowerCase()}</td></tr>
      {accounts.map((a) => (
        <tr key={a.id} className={a.active ? '' : 'inactive'}>
          <td>{a.code}</td>
          <td><Link to={`/accounts/${a.id}`}>{a.name}</Link>{a.systemKey && <span className="tag">system</span>}</td>
          <td>{a.type.toLowerCase()}</td>
          <td className="muted">{a.subtype.replaceAll('_', ' ').toLowerCase()}</td>
          <td className="num">{fmtMoney(a.balance)}</td>
        </tr>
      ))}
    </>
  );
}

function NewAccount({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [form, setForm] = useState({ code: '', name: '', type: 'EXPENSE' as AccountType, subtype: 'OPERATING' });
  const m = useMutation({
    mutationFn: () => post<Account>('/accounts', form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); onClose(); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); m.mutate(); };
  return (
    <Modal title="New account" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="row">
          <label>Code<input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></label>
          <label className="grow">Name<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
        </div>
        <div className="row">
          <label>Type
            <select value={form.type} onChange={(e) => {
              const type = e.target.value as AccountType;
              setForm({ ...form, type, subtype: SUBTYPES[type][0] });
            }}>
              {TYPES.map((t) => <option key={t}>{t}</option>)}
            </select>
          </label>
          <label className="grow">Detail type
            <select value={form.subtype} onChange={(e) => setForm({ ...form, subtype: e.target.value })}>
              {SUBTYPES[form.type].map((s) => <option key={s} value={s}>{s.replaceAll('_', ' ').toLowerCase()}</option>)}
            </select>
          </label>
        </div>
        <ErrorText error={m.error} />
        <div className="form-actions"><button className="btn primary" disabled={m.isPending}>Save</button></div>
      </form>
    </Modal>
  );
}
