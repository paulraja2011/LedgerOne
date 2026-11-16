import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { RegisterLine } from '../api/types';
import { fmtDate, fmtMoney } from '../format';
import { Loading, PageHeader } from '../components/ui';
import { useAccounts } from './AccountsPage';

export default function RegisterPage() {
  const { id } = useParams();
  const accounts = useAccounts();
  const lines = useQuery({ queryKey: ['register', id], queryFn: () => api<RegisterLine[]>(`/accounts/${id}/register`) });
  const account = accounts.data?.find((a) => a.id === id);
  if (!lines.data || !account) return <Loading />;
  return (
    <>
      <PageHeader title={`${account.code} · ${account.name}`}>
        <span className="muted">Balance <strong>{fmtMoney(account.balance)}</strong></span>
      </PageHeader>
      <p><Link to="/accounts">← Chart of accounts</Link></p>
      <div className="card flush">
        <table className="table">
          <thead><tr><th>Date</th><th>Entry</th><th>Source</th><th>Memo</th><th className="num">Debit</th><th className="num">Credit</th><th className="num">Balance</th></tr></thead>
          <tbody>
            {lines.data.map((l, i) => (
              <tr key={i}>
                <td>{fmtDate(l.date)}</td>
                <td>#{l.number}</td>
                <td className="muted">{l.sourceType.toLowerCase().replace('_', ' ')}</td>
                <td>{l.description || l.memo}</td>
                <td className="num">{l.debit > 0 ? fmtMoney(l.debit) : ''}</td>
                <td className="num">{l.credit > 0 ? fmtMoney(l.credit) : ''}</td>
                <td className="num">{fmtMoney(l.balance)}</td>
              </tr>
            ))}
            {lines.data.length === 0 && <tr><td colSpan={7} className="muted">No transactions yet</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
