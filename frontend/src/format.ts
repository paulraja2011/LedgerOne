const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
export const fmtMoney = (v: number | string | null | undefined) => money.format(Number(v ?? 0));
export const fmtDate = (iso?: string) =>
  iso ? new Date(iso + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '';
export const today = () => new Date().toISOString().slice(0, 10);
export const addDays = (iso: string, days: number) => {
  const d = new Date(iso + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};
export const statusLabel = (s: string) => s.replace('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
