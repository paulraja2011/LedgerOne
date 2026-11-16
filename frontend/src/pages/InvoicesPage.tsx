import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Invoice } from '../api/types';
import { fmtDate, fmtMoney } from '../format';
import { Loading, PageHeader, StatusBadge } from '../components/ui';
import { useCustomers } from './CustomersPage';

const FILTERS = ['ALL', 'DRAFT', 'OPEN', 'OVERDUE', 'PAID', 'VOID'] as const;

export default function InvoicesPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const customer = params.get('customer');
  const filter = (params.get('status') ?? 'ALL') as (typeof FILTERS)[number];
  const customers = useCustomers();
  const { data } = useQuery({
    queryKey: ['invoices', customer],
    queryFn: () => api<Invoice[]>(customer ? `/invoices?customerId=${customer}` : '/invoices'),
  });
  if (!data) return <Loading />;
  const rows = data.filter((i) =>
    filter === 'ALL' ? true
      : filter === 'OVERDUE' ? i.overdue
      : filter === 'OPEN' ? i.status === 'OPEN' || i.status === 'PARTIALLY_PAID'
      : i.status === filter);
  const customerName = customers.data?.find((c) => c.id === customer)?.displayName;

  const setFilter = (f: string) => {
    const p = new URLSearchParams(params);
    if (f === 'ALL') p.delete('status'); else p.set('status', f);
    setParams(p);
  };

  return (
    <>
      <PageHeader title={customerName ? `Invoices · ${customerName}` : 'Invoices'}>
        <button className="btn primary" onClick={() => navigate('/invoices/new')}>New invoice</button>
      </PageHeader>
      <div className="tabs">
        {FILTERS.map((f) => (
          <button key={f} className={f === filter ? 'tab active' : 'tab'} onClick={() => setFilter(f)}>
            {f.charAt(0) + f.slice(1).toLowerCase()}
          </button>
        ))}
        {customer && <button className="tab" onClick={() => setParams({})}>Clear customer ✕</button>}
      </div>
      <div className="card flush">
        <table className="table">
          <thead><tr><th>#</th><th>Date</th><th>Customer</th><th>Due</th><th className="num">Total</th><th className="num">Balance</th><th>Status</th></tr></thead>
          <tbody>
            {rows.map((i) => (
              <tr key={i.id} className="clickable" onClick={() => navigate(`/invoices/${i.id}`)}>
                <td><Link to={`/invoices/${i.id}`}>{i.number}</Link></td>
                <td>{fmtDate(i.invoiceDate)}</td>
                <td>{i.customerName}</td>
                <td>{fmtDate(i.dueDate)}</td>
                <td className="num">{fmtMoney(i.total)}</td>
                <td className="num">{fmtMoney(i.balanceDue)}</td>
                <td><StatusBadge status={i.status} overdue={i.overdue} /></td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={7} className="muted">No invoices</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
