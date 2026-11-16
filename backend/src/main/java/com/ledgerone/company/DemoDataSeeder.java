package com.ledgerone.company;

import com.ledgerone.identity.AppUser;
import com.ledgerone.identity.CompanyMember;
import com.ledgerone.identity.MemberRepository;
import com.ledgerone.identity.UserRepository;
import com.ledgerone.ledger.Account;
import com.ledgerone.ledger.AccountRepository;
import com.ledgerone.ledger.AccountType;
import com.ledgerone.ledger.PostingService;
import com.ledgerone.purchases.Bill;
import com.ledgerone.purchases.BillService;
import com.ledgerone.purchases.Vendor;
import com.ledgerone.sales.Customer;
import com.ledgerone.sales.Invoice;
import com.ledgerone.sales.InvoiceService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates a demo company with a default chart of accounts and six months of activity on first start. */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_EMAIL = "admin@ledgerone.dev";
    public static final String DEMO_PASSWORD = "Admin@123";

    private final boolean enabled;
    private final UserRepository users;
    private final MemberRepository members;
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final PostingService posting;
    private final InvoiceService invoices;
    private final BillService bills;
    private final PasswordEncoder encoder;
    private final EntityManager em;

    public DemoDataSeeder(@Value("${ledgerone.seed-demo-data}") boolean enabled, UserRepository users,
                          MemberRepository members, CompanyRepository companies, AccountRepository accounts,
                          PostingService posting, InvoiceService invoices, BillService bills,
                          PasswordEncoder encoder, EntityManager em) {
        this.enabled = enabled;
        this.users = users;
        this.members = members;
        this.companies = companies;
        this.accounts = accounts;
        this.posting = posting;
        this.invoices = invoices;
        this.bills = bills;
        this.encoder = encoder;
        this.em = em;
    }

    /** Default chart of accounts: code, name, type, subtype, system key. */
    public static final List<String[]> DEFAULT_ACCOUNTS = List.of(
            new String[]{"1000", "Business Checking", "ASSET", "BANK", "BANK"},
            new String[]{"1010", "Savings", "ASSET", "BANK", null},
            new String[]{"1200", "Accounts Receivable", "ASSET", "ACCOUNTS_RECEIVABLE", "AR"},
            new String[]{"1500", "Equipment", "ASSET", "FIXED_ASSET", null},
            new String[]{"2000", "Accounts Payable", "LIABILITY", "ACCOUNTS_PAYABLE", "AP"},
            new String[]{"2100", "Business Credit Card", "LIABILITY", "CREDIT_CARD", null},
            new String[]{"2200", "Sales Tax Payable", "LIABILITY", "OTHER_CURRENT_LIABILITY", "SALES_TAX"},
            new String[]{"3000", "Owner's Equity", "EQUITY", "OWNER_EQUITY", "OWNER_EQUITY"},
            new String[]{"3900", "Retained Earnings", "EQUITY", "RETAINED_EARNINGS", "RETAINED_EARNINGS"},
            new String[]{"4000", "Sales", "INCOME", "SALES", "SALES"},
            new String[]{"4100", "Consulting Revenue", "INCOME", "SERVICE", null},
            new String[]{"5000", "Cost of Goods Sold", "EXPENSE", "COST_OF_GOODS_SOLD", null},
            new String[]{"6000", "Rent", "EXPENSE", "OPERATING", null},
            new String[]{"6100", "Utilities", "EXPENSE", "OPERATING", null},
            new String[]{"6200", "Office Supplies", "EXPENSE", "OPERATING", null},
            new String[]{"6300", "Advertising", "EXPENSE", "OPERATING", null},
            new String[]{"6400", "Professional Fees", "EXPENSE", "OPERATING", null},
            new String[]{"6500", "Software Subscriptions", "EXPENSE", "OPERATING", null});

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || users.count() > 0) return;
        log.info("Seeding LedgerOne demo company");

        Company company = new Company();
        company.name = "Demo Trading Co.";
        companies.save(company);
        UUID cid = company.id;

        AppUser admin = new AppUser();
        admin.email = DEMO_EMAIL;
        admin.name = "Demo Admin";
        admin.passwordHash = encoder.encode(DEMO_PASSWORD);
        users.save(admin);
        CompanyMember m = new CompanyMember();
        m.companyId = cid;
        m.userId = admin.id;
        m.role = "ADMIN";
        members.save(m);

        Map<String, UUID> acct = new HashMap<>();
        for (String[] a : DEFAULT_ACCOUNTS) {
            Account x = new Account();
            x.companyId = cid;
            x.code = a[0];
            x.name = a[1];
            x.type = AccountType.valueOf(a[2]);
            x.subtype = a[3];
            x.systemKey = a[4];
            accounts.save(x);
            acct.put(a[0], x.id);
        }

        UUID[] customers = {
                customer(cid, "Acme Corporation", "ap@acme.example"),
                customer(cid, "Blue Harbor Cafe", "owner@blueharbor.example"),
                customer(cid, "Northwind Retail", "billing@northwind.example"),
                customer(cid, "Summit Fitness", "accounts@summit.example")};
        UUID landlord = vendor(cid, "Parkside Properties");
        UUID power = vendor(cid, "City Power & Light");
        UUID cloud = vendor(cid, "CloudSoft Inc.");
        UUID ads = vendor(cid, "AdReach Media");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(5).withDayOfMonth(1);

        posting.post(new PostingService.Request(cid, admin.id, start, "Owner investment", "MANUAL", null, List.of(
                PostingService.Line.debit(acct.get("1000"), bd("40000"), "Opening deposit"),
                PostingService.Line.credit(acct.get("3000"), bd("40000"), "Owner investment"))));

        for (int i = 0; i < 6; i++) {
            LocalDate month = start.plusMonths(i);
            boolean recent = i >= 4;

            // Two invoices a month; older ones are paid in full.
            for (int k = 0; k < 2; k++) {
                UUID cust = customers[(i + k) % customers.length];
                LocalDate d = month.plusDays(4 + k * 10);
                if (d.isAfter(today)) d = today;
                Invoice inv = invoices.create(cid, new InvoiceService.InvoiceInput(cust, d, d.plusDays(30), null, List.of(
                        new InvoiceService.LineInput("Product order", bd(String.valueOf(10 + i * 3 + k * 5)), bd("120"), acct.get("4000")),
                        new InvoiceService.LineInput("Consulting hours", bd(String.valueOf(6 + k * 4)), bd("150"), acct.get("4100")))));
                invoices.approve(cid, admin.id, inv.id);
                if (!recent) {
                    invoices.receivePayment(cid, admin.id, inv.id,
                            new InvoiceService.PaymentInput(d.plusDays(18), inv.total, null, "ACH", null));
                } else if (k == 0 && i == 4) {
                    invoices.receivePayment(cid, admin.id, inv.id,
                            new InvoiceService.PaymentInput(d.plusDays(10), bd("1000"), null, "Check", "1042"));
                }
            }

            // Monthly bills; all but the current month are paid.
            LocalDate bd = month.plusDays(1);
            List<Bill> monthBills = List.of(
                    bill(cid, landlord, "RENT-" + month.getMonthValue(), bd, acct.get("6000"), "Office rent", "2500"),
                    bill(cid, power, "UTIL-" + month.getMonthValue(), bd.plusDays(7), acct.get("6100"), "Electricity", String.valueOf(180 + i * 12)),
                    bill(cid, cloud, "SUB-" + month.getMonthValue(), bd.plusDays(2), acct.get("6500"), "Accounting and CRM licences", "349"),
                    bill(cid, ads, "AD-" + month.getMonthValue(), bd.plusDays(12), acct.get("6300"), "Online campaign", String.valueOf(600 + i * 50)));
            for (Bill b : monthBills) {
                bills.approve(cid, admin.id, b.id);
                if (i < 5) bills.pay(cid, admin.id, b.id, new BillService.PayInput(b.billDate.plusDays(10), null, null));
            }
        }
        log.info("Demo data ready. Sign in with {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private Bill bill(UUID cid, UUID vendor, String ref, LocalDate d, UUID account, String desc, String amount) {
        LocalDate today = LocalDate.now();
        if (d.isAfter(today)) d = today;
        return bills.create(cid, new BillService.BillInput(vendor, ref, d, d.plusDays(15), null,
                List.of(new BillService.LineInput(desc, bd(amount), account))));
    }

    private UUID customer(UUID cid, String name, String email) {
        Customer c = new Customer();
        c.companyId = cid;
        c.displayName = name;
        c.email = email;
        em.persist(c);
        return c.id;
    }

    private UUID vendor(UUID cid, String name) {
        Vendor v = new Vendor();
        v.companyId = cid;
        v.displayName = name;
        em.persist(v);
        return v.id;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
