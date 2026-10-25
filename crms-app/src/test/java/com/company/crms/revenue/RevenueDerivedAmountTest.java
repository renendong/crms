package com.company.crms.revenue;

import com.company.crms.common.exception.BizException;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.support.RevenueRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收入金额派生与字段规则单测：税额 / 不含税 / 毛利、期间校验、收入类型归一化。
 */
class RevenueDerivedAmountTest {

    private RevenueRecord record(String amount, String taxRate, String cost) {
        RevenueRecord r = new RevenueRecord();
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
        r.setCostAmount(cost == null ? null : new BigDecimal(cost));
        return r;
    }

    @Test
    void should_derive_tax_net_and_gross_profit() {
        RevenueRecord r = record("1130.00", "0.13", "100.00");

        RevenueRules.applyDerived(r);

        assertEquals(new BigDecimal("146.90"), r.getTaxAmount());
        assertEquals(new BigDecimal("983.10"), r.getNetAmount());
        assertEquals(new BigDecimal("883.10"), r.getGrossProfit());
        assertEquals(new BigDecimal("1130.00"), r.getAmount());
        assertEquals(new BigDecimal("100.00"), r.getCostAmount());
        assertEquals("SERVICE", r.getIncomeType());
    }

    @Test
    void should_treat_blank_tax_rate_and_cost_as_zero() {
        RevenueRecord r = record("1130.00", null, null);

        RevenueRules.applyDerived(r);

        assertEquals(BigDecimal.ZERO.setScale(2), r.getTaxAmount().setScale(2));
        assertEquals(new BigDecimal("1130.00"), r.getNetAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), r.getCostAmount().setScale(2));
        assertEquals(new BigDecimal("1130.00"), r.getGrossProfit());
    }

    @Test
    void should_round_tax_amount_half_up_to_two_decimals() {
        RevenueRecord r = record("1.00", "0.125", null);

        RevenueRules.applyDerived(r);

        assertEquals(new BigDecimal("0.13"), r.getTaxAmount());
        assertEquals(new BigDecimal("0.87"), r.getNetAmount());
    }

    @Test
    void should_reject_amount_with_three_decimals() {
        RevenueRecord r = record("100.123", "0.13", null);

        BizException ex = assertThrows(BizException.class, () -> RevenueRules.applyDerived(r));
        assertTrue(ex.getMessage().contains("2 位小数"));
    }

    @Test
    void should_reject_amount_not_positive() {
        RevenueRecord r = record("0", "0.13", null);

        BizException ex = assertThrows(BizException.class, () -> RevenueRules.applyDerived(r));
        assertTrue(ex.getMessage().contains("含税金额必须大于 0"));
    }

    @Test
    void should_reject_tax_rate_out_of_range() {
        BizException ex = assertThrows(BizException.class,
                () -> RevenueRules.applyDerived(record("100.00", "1.01", null)));
        assertTrue(ex.getMessage().contains("税率必须在 0~1 之间"));

        BizException negative = assertThrows(BizException.class,
                () -> RevenueRules.applyDerived(record("100.00", "-0.01", null)));
        assertTrue(negative.getMessage().contains("税率必须在 0~1 之间"));
    }

    @Test
    void should_reject_negative_cost() {
        BizException ex = assertThrows(BizException.class,
                () -> RevenueRules.applyDerived(record("100.00", "0.13", "-1")));
        assertTrue(ex.getMessage().contains("成本金额不能小于 0"));
    }

    @Test
    void should_normalize_income_type() {
        assertEquals("GOODS", RevenueRules.normalizeIncomeType("商品"));
        assertEquals("GOODS", RevenueRules.normalizeIncomeType("GOODS"));
        assertEquals("SERVICE", RevenueRules.normalizeIncomeType("服务"));
        assertEquals("SERVICE", RevenueRules.normalizeIncomeType(null));
        assertEquals("OTHER", RevenueRules.normalizeIncomeType("其他"));
        assertThrows(IllegalArgumentException.class, () -> RevenueRules.normalizeIncomeType("未知类型"));
    }

    @Test
    void should_validate_period_month() {
        assertEquals("2026-04", RevenueRules.checkPeriodMonth("2026-04"));

        BizException format = assertThrows(BizException.class,
                () -> RevenueRules.checkPeriodMonth("2026-13"));
        assertEquals("RV-003", format.getCode());

        String future = YearMonth.now().plusMonths(1).toString();
        BizException later = assertThrows(BizException.class,
                () -> RevenueRules.checkPeriodMonth(future));
        assertEquals("RV-003", later.getCode());
        assertTrue(later.getMessage().contains("不能晚于当前月"));
    }
}
