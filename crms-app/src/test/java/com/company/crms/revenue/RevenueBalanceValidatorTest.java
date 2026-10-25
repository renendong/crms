package com.company.crms.revenue;

import com.company.crms.common.exception.BizException;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.mapper.PaymentRecordMapper;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.payment.vo.PaymentPlanSummaryVO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.impl.RevenueBalanceValidatorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收入确认时点平衡 + 合同级合计校验单测。
 *
 * <p>公式：截至时点 T 的含税收入累计 = T 及以前的实际回款（仅 NORMAL）+ T 之后计划的待核销金额。
 */
@ExtendWith(MockitoExtension.class)
class RevenueBalanceValidatorTest {

    @Mock ContractMapper contractMapper;
    @Mock RevenueRecordMapper revenueMapper;
    @Mock PaymentRecordMapper recordMapper;
    @Mock PaymentPlanMapper planMapper;
    @Mock PaymentPlanService planService;

    @InjectMocks RevenueBalanceValidatorImpl validator;

    private Contract contract(String amount) {
        Contract c = new Contract();
        c.setId(1L);
        c.setCode("CT-A");
        c.setAmount(new BigDecimal(amount));
        return c;
    }

    private PaymentPlanSummaryVO planSummary(String contractAmount, String planned, long count) {
        PaymentPlanSummaryVO vo = new PaymentPlanSummaryVO();
        vo.setContractId(1L);
        vo.setContractAmount(new BigDecimal(contractAmount));
        vo.setPlannedAmount(new BigDecimal(planned));
        vo.setDiff(new BigDecimal(contractAmount).subtract(new BigDecimal(planned)));
        vo.setPlanCount(count);
        vo.setComplete(vo.getDiff().signum() == 0);
        return vo;
    }

    private RevenueRecord revenue(LocalDate date, String amount) {
        RevenueRecord r = new RevenueRecord();
        r.setContractId(1L);
        r.setRecognizeDate(date);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void should_pass_when_timepoint_balance_and_contract_total_hold() {
        LocalDate t = LocalDate.of(2026, 4, 1);
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "10000.00", 4));
        when(revenueMapper.sumAmountUpTo(1L, t, null)).thenReturn(new BigDecimal("2000.00"));
        when(recordMapper.sumEffectiveAmountUpTo(1L, t)).thenReturn(new BigDecimal("3000.00"));
        when(planMapper.sumUnsettledAfter(1L, t)).thenReturn(new BigDecimal("3000.00"));
        when(revenueMapper.sumAmountByContract(1L, null)).thenReturn(new BigDecimal("6000.00"));

        assertDoesNotThrow(() -> validator.validateContract(1L, List.of(revenue(t, "4000.00")), null));

        // 回款侧必须使用「有效回款」口径：已红冲 / 红字单不参与 RECEIVED
        verify(recordMapper).sumEffectiveAmountUpTo(1L, t);
        // 后续计划取严格晚于 T 的待核销金额，plan_date == T 的当期待收不计入
        verify(planMapper).sumUnsettledAfter(1L, t);
    }

    @Test
    void should_reject_when_income_exceeds_timepoint_right_side() {
        LocalDate t = LocalDate.of(2026, 4, 1);
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "10000.00", 4));
        when(revenueMapper.sumAmountUpTo(1L, t, null)).thenReturn(new BigDecimal("2000.00"));
        when(recordMapper.sumEffectiveAmountUpTo(1L, t)).thenReturn(new BigDecimal("4000.00"));
        when(planMapper.sumUnsettledAfter(1L, t)).thenReturn(new BigDecimal("1000.00"));

        BizException ex = assertThrows(BizException.class,
                () -> validator.validateContract(1L, List.of(revenue(t, "4000.00")), null));

        assertEquals("RV-004", ex.getCode());
        assertTrue(ex.getMessage().contains("含税收入累计 6000.00"));
        assertTrue(ex.getMessage().contains("已回款 4000.00"));
        assertTrue(ex.getMessage().contains("后续计划待收 1000.00"));
    }

    @Test
    void should_reject_when_income_is_less_than_timepoint_right_side() {
        LocalDate t = LocalDate.of(2026, 4, 1);
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "10000.00", 4));
        when(revenueMapper.sumAmountUpTo(1L, t, null)).thenReturn(new BigDecimal("2000.00"));
        when(recordMapper.sumEffectiveAmountUpTo(1L, t)).thenReturn(new BigDecimal("5000.00"));
        when(planMapper.sumUnsettledAfter(1L, t)).thenReturn(new BigDecimal("4000.00"));

        BizException ex = assertThrows(BizException.class,
                () -> validator.validateContract(1L, List.of(revenue(t, "4000.00")), null));

        assertEquals("RV-004", ex.getCode());
        assertTrue(ex.getMessage().contains("差额 -3000.00"));
    }

    @Test
    void should_reject_when_contract_has_no_plan() {
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "0", 0));

        BizException ex = assertThrows(BizException.class,
                () -> validator.validateContract(1L, List.of(revenue(LocalDate.of(2026, 4, 1), "4000.00")), null));

        assertEquals("RV-005", ex.getCode());
    }

    @Test
    void should_reject_when_plan_total_is_incomplete() {
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "8000.00", 2));

        BizException ex = assertThrows(BizException.class,
                () -> validator.validateContract(1L, List.of(revenue(LocalDate.of(2026, 4, 1), "4000.00")), null));

        assertEquals("PM-002", ex.getCode());
        assertTrue(ex.getMessage().contains("请先补全回款计划"));
    }

    @Test
    void should_reject_when_contract_income_total_mismatch() {
        LocalDate t = LocalDate.of(2026, 4, 1);
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "10000.00", 4));
        when(revenueMapper.sumAmountUpTo(1L, t, null)).thenReturn(new BigDecimal("2000.00"));
        when(recordMapper.sumEffectiveAmountUpTo(1L, t)).thenReturn(new BigDecimal("3000.00"));
        when(planMapper.sumUnsettledAfter(1L, t)).thenReturn(new BigDecimal("3000.00"));
        when(revenueMapper.sumAmountByContract(1L, null)).thenReturn(new BigDecimal("5000.00"));

        BizException ex = assertThrows(BizException.class,
                () -> validator.validateContract(1L, List.of(revenue(t, "4000.00")), null));

        assertEquals("RV-002", ex.getCode());
        assertTrue(ex.getMessage().contains("同一合同含税收入合计 9000.00"));
        assertTrue(ex.getMessage().contains("差额 1000.00"));
    }

    @Test
    void should_validate_each_timepoint_in_ascending_order() {
        LocalDate t1 = LocalDate.of(2026, 4, 1);
        LocalDate t2 = LocalDate.of(2026, 5, 1);
        when(contractMapper.selectById(1L)).thenReturn(contract("10000.00"));
        when(planService.summary(1L)).thenReturn(planSummary("10000.00", "10000.00", 4));
        // T1：批内累计 4000，历史收入 0
        when(revenueMapper.sumAmountUpTo(1L, t1, null)).thenReturn(BigDecimal.ZERO);
        when(recordMapper.sumEffectiveAmountUpTo(1L, t1)).thenReturn(new BigDecimal("1000.00"));
        when(planMapper.sumUnsettledAfter(1L, t1)).thenReturn(new BigDecimal("3000.00"));
        // T2：批内累计 7000，历史收入 3000
        when(revenueMapper.sumAmountUpTo(1L, t2, null)).thenReturn(new BigDecimal("3000.00"));
        when(recordMapper.sumEffectiveAmountUpTo(1L, t2)).thenReturn(new BigDecimal("4000.00"));
        when(planMapper.sumUnsettledAfter(1L, t2)).thenReturn(new BigDecimal("6000.00"));
        when(revenueMapper.sumAmountByContract(1L, null)).thenReturn(new BigDecimal("3000.00"));

        assertDoesNotThrow(() -> validator.validateContract(1L, List.of(
                revenue(t2, "3000.00"), revenue(t1, "4000.00")), null));
    }
}
