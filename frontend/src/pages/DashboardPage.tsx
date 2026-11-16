import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Dashboard, Invoice } from '../api/types';
import { fmtDate, fmtMoney } from '../format';
import { Loading, PageHeader, StatusBadge } from '../components/ui';

export default function DashboardPage() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: () => api<Dashboard>('/reports/dashboard') });
  const invoices = useQuery({ queryKey: ['invoices'], queryFn: () => api<Invoice[]>('/invoices') });
  if (!dash.data) return <Loading />;
  const d = dash.data;
  const max = Math.max(1, ...d.monthly.flatMap((m) => [m.income, m.expenses]));
  const open = (invoices.data ?? []).filter((i) => i.status === 'OPEN' || i.status === 'PARTIALLY_PAID').slice(0, 6);

  return (
    <>
      <PageHeader title="Dashboard" />
      <div className="kpis">
        <Kpi label="Cash in bank" value={fmtMoney(d.cash)} />
        <Kpi label="Receivables" value={fmtMoney(d.receivables)} sub={`${d.openInvoices} open invoices`} />
        <Kpi label="Overdue" value={fmtMoney(d.overdueReceivables)} tone={d.overdueReceivables > 0 ? 'warn' : undefined} />
        <Kpi label="Payables" value={fmtMoney(d.payables)} />
        <Kpi label="Net income YTD" value={fmtMoney(d.netIncomeYtd)} tone={d.netIncomeYtd >= 0 ? 'good' : 'bad'} />
      </div>
      <div className="grid-2">
        <section className="card">
          <h2>Income vs expenses, last 6 months</h2>
          <div className="bars">
            {d.monthly.map((m) => (
              <div className="bar-group" key={m.month}>
                <div className="bar-pair">
                  <div className="bar income" style={{ height: `${(m.income / max) * 100}%` }} title={`Income ${fmtMoney(m.income)}`} />
                  <div className="bar expense" style={{ height: `${(m.expenses / max) * 100}%` }} title={`Expenses ${fmtMoney(m.expenses)}`} />
                </div>
                <div className="bar-label">{new Date(m.month + '-01T00:00:00').toLocaleDateString('en-US', { month: 'short' })}</div>
              </div>
            ))}
          </div>
          <div className="legend"><span className="sw income" /> Income <span className="sw expense" /> Expenses</div>
        </section>
        <section className="card">
          <h2>Open invoices</h2>
          <table className="table">
            <thead><tr><th>#</th><th>Customer</th><th>Due</th><th className="num">Balance</th><th /></tr></thead>
            <tbody>
              {open.map((i) => (
                <tr key={i.id}>
                  <td><Link to={`/invoices/${i.id}`}>{i.number}</Link></td>
                  <td>{i.customerName}</td>
                  <td>{fmtDate(i.dueDate)}</td>
                  <td className="num">{fmtMoney(i.balanceDue)}</td>
                  <td><StatusBadge status={i.status} overdue={i.overdue} /></td>
                </tr>
              ))}
              {open.length === 0 && <tr><td colSpan={5} className="muted">No open invoices</td></tr>}
            </tbody>
          </table>
        </section>
      </div>
    </>
  );
}

function Kpi({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: 'good' | 'bad' | 'warn' }) {
  return (
    <div className={`card kpi ${tone ?? ''}`}>
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {sub && <div className="muted small">{sub}</div>}
    </div>
  );
}
