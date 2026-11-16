import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Vendor } from '../api/types';
import { fmtMoney } from '../format';
import { Loading, PageHeader } from '../components/ui';
import { ContactForm } from './CustomersPage';

export const useVendors = () => useQuery({ queryKey: ['vendors'], queryFn: () => api<Vendor[]>('/vendors') });

export default function VendorsPage() {
  const { data } = useVendors();
  const [editing, setEditing] = useState<Vendor | 'new' | null>(null);
  if (!data) return <Loading />;
  return (
    <>
      <PageHeader title="Vendors">
        <button className="btn primary" onClick={() => setEditing('new')}>New vendor</button>
      </PageHeader>
      <div className="card flush">
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th className="num">Open balance</th><th /></tr></thead>
          <tbody>
            {data.map((v) => (
              <tr key={v.id}>
                <td>{v.displayName}</td>
                <td>{v.email}</td>
                <td>{v.phone}</td>
                <td className="num">{fmtMoney(v.openBalance)}</td>
                <td className="num"><button className="btn small" onClick={() => setEditing(v)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && <ContactForm kind="vendor" initial={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}
