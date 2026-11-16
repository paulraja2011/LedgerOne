import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { AgingRow, BalanceSheet, ProfitAndLoss, ReportSection, TrialBalance } from '../api/types';
import { fmtDate, fmtMoney, today } from '../format';
import { Loading, PageHeader } from '../components/ui';

type Tab = 'pl' | 'bs' | 'tb' | 'aging';
const TABS: [Tab, string][] = [['pl', 'Profit and loss'], ['bs', 'Balance sheet'], ['tb', 'Trial balance'], ['aging', 'A/R aging']];

export default function ReportsPage() {
  const [tab, setTab] = useState<Tab>('pl');
  const [from, setFrom] = useState(today().slice(0, 4) + '-01-01');
  const [to, setTo] = useState(today());
  return (
    <>
      <PageHeader title="Reports">
        {tab === 'pl' && <label className="inline">From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>}
        {tab !== 'aging' && <label className="inline">{tab === 'pl' ? 'To' : 'As of'} <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>}
        <button className="btn" onClick={() => window.print()}>Print</button>
      </PageHeader>
      <div className="tabs">
        {TABS.map(([k, label]) => <button key={k} className={k === tab ? 'tab active' : 'tab'} onClick={() => setTab(k)}>{label}</button>)}
      </div>
      <div className="card report">
        {tab === 'pl' && <PL from={from} to={to} />}
        {tab === 'bs' && <BS asOf={to} />}
        {tab === 'tb' && <TB asOf={to} />}
        {tab === 'aging' && <Aging />}
      </div>
    </>
  );
}

function SectionRows({ s }: { s: ReportSection }) {
  return (
    <>
      <tr className="group-row"><td colSpan={2}>{s.title}</td></tr>
      {s.items.map((i, n) => (
        <tr key={n}>
          <td className="indent">{i.accountId ? <Link to={`/accounts/${i.accountId}`}>{i.name}</Link> : <em>{i.name}</em>}</td>
          <td className="num">{fmtMoney(i.amount)}</td>
        </tr>
      ))}
      <tr className="subtotal"><td>Total {s.title.toLowerCase()}</td><td className="num">{fmtMoney(s.total)}</td></tr>
    </>
  );
}

function PL({ from, to }: { from: string; to: string }) {
  const { data } = useQuery({ queryKey: ['pl', from, to], queryFn: () => api<ProfitAndLoss>(`/reports/profit-and-loss?from=${from}&to=${to}`) });
  if (!data) return <Loading />;
  return (
    <>
      <h2 className="center">Profit and loss</h2>
      <p className="center muted">{fmtDate(data.from)} – {fmtDate(data.to)} · accrual basis</p>
      <table className="table report-table">
        <tbody>
          <SectionRows s={data.income} />
          <SectionRows s={data.expenses} />
          <tr className="grand"><td>Net income</td><td className={`num ${data.netIncome < 0 ? 'bad-text' : ''}`}>{fmtMoney(data.netIncome)}</td></tr>
        </tbody>
      </table>
    </>
  );
}

function BS({ asOf }: { asOf: string }) {
  const { data } = useQuery({ queryKey: ['bs', asOf], queryFn: () => api<BalanceSheet>(`/reports/balance-sheet?asOf=${asOf}`) });
  if (!data) return <Loading />;
  return (
    <>
      <h2 className="center">Balance sheet</h2>
      <p className="center muted">As of {fmtDate(data.asOf)} {data.balanced ? '· balanced ✓' : '· OUT OF BALANCE'}</p>
      <table className="table report-table">
        <tbody>
          <SectionRows s={data.assets} />
          <SectionRows s={data.liabilities} />
          <SectionRows s={data.equity} />
          <tr className="grand"><td>Total liabilities and equity</td><td className="num">{fmtMoney(data.totalLiabilitiesAndEquity)}</td></tr>
        </tbody>
      </table>
    </>
  );
}

function TB({ asOf }: { asOf: string }) {
  const { data } = useQuery({ queryKey: ['tb', asOf], queryFn: () => api<TrialBalance>(`/reports/trial-balance?asOf=${asOf}`) });
  if (!data) return <Loading />;
  return (
    <>
      <h2 className="center">Trial balance</h2>
      <p className="center muted">As of {fmtDate(data.asOf)}</p>
      <table className="table report-table">
        <thead><tr><th>Account</th><th className="num">Debit</th><th className="num">Credit</th></tr></thead>
        <tbody>
          {data.lines.map((l) => (
            <tr key={l.accountId}>
              <td><Link to={`/accounts/${l.accountId}`}>{l.code} · {l.name}</Link></td>
              <td className="num">{l.debit ? fmtMoney(l.debit) : ''}</td>
              <td className="num">{l.credit ? fmtMoney(l.credit) : ''}</td>
            </tr>
          ))}
          <tr className="grand"><td>Total</td><td className="num">{fmtMoney(data.totalDebit)}</td><td className="num">{fmtMoney(data.totalCredit)}</td></tr>
        </tbody>
      </table>
    </>
  );
}

function Aging() {
  const { data } = useQuery({ queryKey: ['aging'], queryFn: () => api<AgingRow[]>('/reports/ar-aging') });
  if (!data) return <Loading />;
  const sum = (k: keyof AgingRow) => data.reduce((s, r) => s + Number(r[k]), 0);
  const cols: [keyof AgingRow, string][] = [['current', 'Current'], ['d1to30', '1–30'], ['d31to60', '31–60'], ['d61to90', '61–90'], ['over90', '90+'], ['total', 'Total']];
  return (
    <>
      <h2 className="center">Accounts receivable aging</h2>
      <p className="center muted">As of {fmtDate(today())} · days past due</p>
      <table className="table report-table">
        <thead><tr><th>Customer</th>{cols.map(([, l]) => <th key={l} className="num">{l}</th>)}</tr></thead>
        <tbody>
          {data.map((r) => (
            <tr key={r.customerId}>
              <td><Link to={`/invoices?customer=${r.customerId}`}>{r.customerName}</Link></td>
              {cols.map(([k]) => <td key={k} className="num">{Number(r[k]) ? fmtMoney(r[k] as number) : '–'}</td>)}
            </tr>
          ))}
          <tr className="grand"><td>Total</td>{cols.map(([k]) => <td key={k} className="num">{fmtMoney(sum(k))}</td>)}</tr>
        </tbody>
      </table>
    </>
  );
}
