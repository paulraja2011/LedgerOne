import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import AccountsPage from './pages/AccountsPage';
import RegisterPage from './pages/RegisterPage';
import JournalPage from './pages/JournalPage';
import CustomersPage from './pages/CustomersPage';
import InvoicesPage from './pages/InvoicesPage';
import InvoicePage from './pages/InvoicePage';
import VendorsPage from './pages/VendorsPage';
import BillsPage from './pages/BillsPage';
import ReportsPage from './pages/ReportsPage';

export default function App() {
  const { session } = useAuth();
  if (!session) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="accounts/:id" element={<RegisterPage />} />
        <Route path="journal" element={<JournalPage />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="invoices" element={<InvoicesPage />} />
        <Route path="invoices/:id" element={<InvoicePage />} />
        <Route path="vendors" element={<VendorsPage />} />
        <Route path="bills" element={<BillsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
