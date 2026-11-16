package com.ledgerone.reporting;

import com.ledgerone.ledger.AccountType;
import com.ledgerone.shared.Tenant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Financial reports, always derived from posted journal lines. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);
    private final JdbcTemplate jdbc;

    public ReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID accountId, String code, String name, AccountType type, String subtype,
                      BigDecimal debit, BigDecimal credit) {
        /** Balance in the account's natural direction. */
        BigDecimal natural() { return type.debitNormal() ? debit.subtract(credit) : credit.subtract(debit); }
    }

    public record Item(UUID accountId, String code, String name, BigDecimal amount) {}
    public record Section(String title, List<Item> items, BigDecimal total) {}

    private List<Row> balances(UUID cid, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select a.id, a.code, a.name, a.type, a.subtype, coalesce(t.d, 0), coalesce(t.c, 0)
                from account a
                left join (select l.account_id, sum(l.debit) d, sum(l.credit) c
                           from journal_line l join journal_entry e on e.id = l.entry_id
                           where l.company_id = ? and e.entry_date between ? and ?
                           group by l.account_id) t on t.account_id = a.id
                where a.company_id = ?
                order by a.code
                """, (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                AccountType.valueOf(rs.getString(4)), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7)),
                cid, from, to, cid);
    }

    private static Section section(String title, List<Row> rows, AccountType type) {
        List<Item> items = rows.stream().filter(r -> r.type() == type && r.natural().signum() != 0)
                .map(r -> new Item(r.accountId(), r.code(), r.name(), r.natural())).toList();
        return new Section(title, items, items.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ---------- Profit and loss ----------

    public record ProfitAndLoss(LocalDate from, LocalDate to, Section income, Section expenses, BigDecimal netIncome) {}

    @GetMapping("/profit-and-loss")
    public ProfitAndLoss profitAndLoss(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.withDayOfYear(1);
        List<Row> rows = balances(Tenant.companyId(), f, t);
        Section inc = section("Income", rows, AccountType.INCOME);
        Section exp = section("Expenses", rows, AccountType.EXPENSE);
        return new ProfitAndLoss(f, t, inc, exp, inc.total().subtract(exp.total()));
    }

    // ---------- Balance sheet ----------

    public record BalanceSheet(LocalDate asOf, Section assets, Section liabilities, Section equity,
                               BigDecimal totalLiabilitiesAndEquity, boolean balanced) {}

    @GetMapping("/balance-sheet")
    public BalanceSheet balanceSheet(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate d = asOf != null ? asOf : LocalDate.now();
        List<Row> rows = balances(Tenant.companyId(), BEGINNING, d);
        Section assets = section("Assets", rows, AccountType.ASSET);
        Section liabilities = section("Liabilities", rows, AccountType.LIABILITY);
        Section eq = section("Equity", rows, AccountType.EQUITY);
        BigDecimal netIncome = section("", rows, AccountType.INCOME).total().subtract(section("", rows, AccountType.EXPENSE).total());
        List<Item> eqItems = new ArrayList<>(eq.items());
        if (netIncome.signum() != 0) eqItems.add(new Item(null, "", "Net income (not yet closed)", netIncome));
        Section equity = new Section("Equity", eqItems, eq.total().add(netIncome));
        BigDecimal le = liabilities.total().add(equity.total());
        return new BalanceSheet(d, assets, liabilities, equity, le, le.compareTo(assets.total()) == 0);
    }

    // ---------- Trial balance ----------

    public record TbLine(UUID accountId, String code, String name, AccountType type, BigDecimal debit, BigDecimal credit) {}
    public record TrialBalance(LocalDate asOf, List<TbLine> lines, BigDecimal totalDebit, BigDecimal totalCredit) {}

    @GetMapping("/trial-balance")
    public TrialBalance trialBalance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate d = asOf != null ? asOf : LocalDate.now();
        List<TbLine> lines = new ArrayList<>();
        BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
        for (Row r : balances(Tenant.companyId(), BEGINNING, d)) {
            BigDecimal net = r.debit().subtract(r.credit());
            if (net.signum() == 0) continue;
            BigDecimal dr = net.signum() > 0 ? net : BigDecimal.ZERO;
            BigDecimal cr = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
            lines.add(new TbLine(r.accountId(), r.code(), r.name(), r.type(), dr, cr));
            td = td.add(dr);
            tc = tc.add(cr);
        }
        return new TrialBalance(d, lines, td, tc);
    }

    // ---------- AR aging ----------

    public record AgingRow(UUID customerId, String customerName, BigDecimal current, BigDecimal d1to30,
                           BigDecimal d31to60, BigDecimal d61to90, BigDecimal over90, BigDecimal total) {}

    @GetMapping("/ar-aging")
    public List<AgingRow> arAging() {
        return jdbc.query("""
                select c.id, c.display_name,
                  sum(case when i.due_date >= current_date then i.total - i.amount_paid else 0 end),
                  sum(case when current_date - i.due_date between 1 and 30 then i.total - i.amount_paid else 0 end),
                  sum(case when current_date - i.due_date between 31 and 60 then i.total - i.amount_paid else 0 end),
                  sum(case when current_date - i.due_date between 61 and 90 then i.total - i.amount_paid else 0 end),
                  sum(case when current_date - i.due_date > 90 then i.total - i.amount_paid else 0 end),
                  sum(i.total - i.amount_paid)
                from invoice i join customer c on c.id = i.customer_id
                where i.company_id = ? and i.status in ('OPEN','PARTIALLY_PAID')
                group by c.id, c.display_name order by c.display_name
                """, (rs, n) -> new AgingRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)),
                Tenant.companyId());
    }

    // ---------- Dashboard ----------

    public record MonthPoint(String month, BigDecimal income, BigDecimal expenses) {}
    public record Dashboard(BigDecimal cash, BigDecimal receivables, BigDecimal overdueReceivables, BigDecimal payables,
                            BigDecimal netIncomeYtd, long openInvoices, List<MonthPoint> monthly) {}

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        UUID cid = Tenant.companyId();
        LocalDate today = LocalDate.now();
        List<Row> all = balances(cid, BEGINNING, today);
        BigDecimal cash = all.stream().filter(r -> "BANK".equals(r.subtype())).map(Row::natural).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> ar = jdbc.queryForMap("""
                select coalesce(sum(total - amount_paid), 0) open,
                       coalesce(sum(case when due_date < current_date then total - amount_paid else 0 end), 0) overdue,
                       count(*) cnt
                from invoice where company_id = ? and status in ('OPEN','PARTIALLY_PAID')
                """, cid);
        BigDecimal ap = jdbc.queryForObject(
                "select coalesce(sum(total - amount_paid), 0) from bill where company_id = ? and status in ('OPEN','PARTIALLY_PAID')",
                BigDecimal.class, cid);
        List<Row> ytd = balances(cid, today.withDayOfYear(1), today);
        BigDecimal netYtd = section("", ytd, AccountType.INCOME).total().subtract(section("", ytd, AccountType.EXPENSE).total());

        List<MonthPoint> monthly = new ArrayList<>();
        YearMonth start = YearMonth.from(today).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            YearMonth ym = start.plusMonths(i);
            List<Row> m = balances(cid, ym.atDay(1), ym.atEndOfMonth());
            monthly.add(new MonthPoint(ym.toString(), section("", m, AccountType.INCOME).total(), section("", m, AccountType.EXPENSE).total()));
        }
        return new Dashboard(cash, (BigDecimal) ar.get("open"), (BigDecimal) ar.get("overdue"), ap, netYtd,
                ((Number) ar.get("cnt")).longValue(), monthly);
    }
}
