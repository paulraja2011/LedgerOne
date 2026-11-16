export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE';
export type DocStatus = 'DRAFT' | 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'VOID';

export interface User { id: string; name: string; email: string; role: string }
export interface CompanyRef { id: string; name: string; baseCurrency: string }
export interface LoginResponse { token: string; user: User; company: CompanyRef }

export interface Account {
  id: string; code: string; name: string; type: AccountType; subtype: string;
  parentId?: string; systemKey?: string; active: boolean; balance: number;
}

export interface RegisterLine {
  entryId: string; number: number; date: string; memo: string; sourceType: string;
  description: string; debit: number; credit: number; balance: number;
}

export interface JournalLine { accountId: string; accountCode: string; accountName: string; debit: number; credit: number; description?: string }
export interface JournalEntry {
  id: string; number: number; date: string; memo?: string; sourceType: string; sourceId?: string;
  reversed: boolean; reversalOfId?: string; total: number; lines: JournalLine[];
}
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export interface Customer {
  id: string; displayName: string; email?: string; phone?: string; billingAddress?: string;
  openBalance: number; overdueBalance: number;
}
export interface Vendor { id: string; displayName: string; email?: string; phone?: string; address?: string; openBalance: number }

export interface InvoiceLine { description: string; quantity: number; rate: number; amount: number; accountId: string }
export interface Payment {
  id: string; invoiceId: string; invoiceNumber: number; customerId: string; customerName: string;
  date: string; amount: number; method?: string; reference?: string; journalEntryId: string;
}
export interface Invoice {
  id: string; number: number; customerId: string; customerName: string; invoiceDate: string; dueDate: string;
  status: DocStatus; overdue: boolean; total: number; amountPaid: number; balanceDue: number; memo?: string;
  journalEntryId?: string; lines: InvoiceLine[]; payments: Payment[];
}

export interface BillLine { description: string; amount: number; accountId: string }
export interface Bill {
  id: string; reference?: string; vendorId: string; vendorName: string; billDate: string; dueDate: string;
  status: DocStatus; overdue: boolean; total: number; amountPaid: number; balanceDue: number; memo?: string;
  journalEntryId?: string; lines: BillLine[];
}

export interface ReportItem { accountId?: string; code: string; name: string; amount: number }
export interface ReportSection { title: string; items: ReportItem[]; total: number }
export interface ProfitAndLoss { from: string; to: string; income: ReportSection; expenses: ReportSection; netIncome: number }
export interface BalanceSheet {
  asOf: string; assets: ReportSection; liabilities: ReportSection; equity: ReportSection;
  totalLiabilitiesAndEquity: number; balanced: boolean;
}
export interface TrialBalance {
  asOf: string; totalDebit: number; totalCredit: number;
  lines: { accountId: string; code: string; name: string; type: AccountType; debit: number; credit: number }[];
}
export interface AgingRow {
  customerId: string; customerName: string; current: number; d1to30: number; d31to60: number;
  d61to90: number; over90: number; total: number;
}
export interface Dashboard {
  cash: number; receivables: number; overdueReceivables: number; payables: number; netIncomeYtd: number;
  openInvoices: number; monthly: { month: string; income: number; expenses: number }[];
}
