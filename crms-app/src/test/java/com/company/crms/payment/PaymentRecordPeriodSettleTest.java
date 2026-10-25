package com.company.crms.payment;

import com.company.crms.common.exception.BizException;
import com.company.crms.payment.entity.PaymentPlan;
import com.company.crms.payment.entity.PaymentRecord;
import com.company.crms.payment.entity.PaymentSettlement;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.mapper.PaymentRecordMapper;
import com.company.crms.payment.mapper.PaymentSettlementMapper;
import com.company.crms.payment.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 实际回款按合同 + 期数自动核销单测。
 *
 * <p>覆盖期数必填、期数不存在、全额 / 部分核销、已结清与红冲拒绝。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordPeriodSettleTest {

    @Mock PaymentPlanMapper planMapper;
    @Mock PaymentRecordMapper recordMapper;
    @Mock PaymentSettlementMapper settlementMapper;

    @InjectMocks SettlementServiceImpl service;

    private PaymentRecord record(String amount) {
        PaymentRecord r = new PaymentRecord();
        r.setId(100L);
        r.setContractId(1L);
        r.setAmount(new BigDecimal(amount));
        r.setUnallocatedAmount(new BigDecimal(amount));
        r.setStatus("NORMAL");
        r.setVersion(0);
        return r;
    }

    private PaymentPlan plan(String unsettled, String settled) {
        PaymentPlan p = new PaymentPlan();
        p.setId(55L);
        p.setContractId(1L);
        p.setPeriodNo(2);
        p.setPlanAmount(new BigDecimal(unsettled).add(new BigDecimal(settled)));
        p.setSettledAmount(new BigDecimal(settled));
        p.setUnsettledAmount(new BigDecimal(unsettled));
        p.setStatus("PENDING");
        p.setPlanDate(LocalDate.of(2026, 2, 1));
        p.setVersion(4);
        return p;
    }

    @Test
    void should_require_period_no() {
        BizException ex = assertThrows(BizException.class,
                () -> service.settleByPeriod(record("1000.00"), null));

        assertEquals("PM-008", ex.getCode());
        verifyNoInteractions(recordMapper, planMapper, settlementMapper);
    }

    @Test
    void should_reject_when_period_plan_not_found() {
        when(recordMapper.selectForUpdate(100L)).thenReturn(record("1000.00"));
        when(planMapper.selectByContractAndPeriodForUpdate(1L, 2)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.settleByPeriod(record("1000.00"), 2));

        assertEquals("PM-001", ex.getCode());
        assertTrue(ex.getMessage().contains("第 2 期回款计划不存在"));
        verify(settlementMapper, never()).insert(any());
    }

    @Test
    void should_settle_full_and_mark_plan_settled() {
        when(recordMapper.selectForUpdate(100L)).thenReturn(record("1000.00"));
        when(planMapper.selectByContractAndPeriodForUpdate(1L, 2)).thenReturn(plan("1000.00", "0.00"));
        when(planMapper.updateById(any())).thenReturn(1);

        BigDecimal settled = service.settleByPeriod(record("1000.00"), 2);

        assertEquals(new BigDecimal("1000.00"), settled);

        ArgumentCaptor<PaymentSettlement> stCap = ArgumentCaptor.forClass(PaymentSettlement.class);
        verify(settlementMapper).insert(stCap.capture());
        assertEquals(new BigDecimal("1000.00"), stCap.getValue().getSettleAmount());
        assertEquals(55L, stCap.getValue().getPaymentPlanId());
        assertEquals("AUTO", stCap.getValue().getSettleType());

        ArgumentCaptor<PaymentPlan> planCap = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(planMapper).updateById(planCap.capture());
        assertEquals(BigDecimal.ZERO.setScale(2), planCap.getValue().getUnsettledAmount().setScale(2));
        assertEquals(new BigDecimal("1000.00"), planCap.getValue().getSettledAmount());
        assertEquals("SETTLED", planCap.getValue().getStatus());

        ArgumentCaptor<PaymentRecord> recCap = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(recordMapper).updateById(recCap.capture());
        assertEquals(BigDecimal.ZERO.setScale(2), recCap.getValue().getUnallocatedAmount().setScale(2));
    }

    @Test
    void should_settle_partially_and_keep_unallocated_amount() {
        when(recordMapper.selectForUpdate(100L)).thenReturn(record("800.00"));
        when(planMapper.selectByContractAndPeriodForUpdate(1L, 2)).thenReturn(plan("1000.00", "0.00"));
        when(planMapper.updateById(any())).thenReturn(1);

        BigDecimal settled = service.settleByPeriod(record("800.00"), 2);

        assertEquals(new BigDecimal("800.00"), settled);

        ArgumentCaptor<PaymentPlan> planCap = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(planMapper).updateById(planCap.capture());
        assertEquals(new BigDecimal("200.00"), planCap.getValue().getUnsettledAmount());
        assertEquals("PARTIAL", planCap.getValue().getStatus());

        ArgumentCaptor<PaymentRecord> recCap = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(recordMapper).updateById(recCap.capture());
        assertEquals(BigDecimal.ZERO.setScale(2), recCap.getValue().getUnallocatedAmount().setScale(2));
    }

    @Test
    void should_reject_already_settled_plan() {
        PaymentPlan settledPlan = plan("0.00", "1000.00");
        settledPlan.setStatus("SETTLED");

        when(recordMapper.selectForUpdate(100L)).thenReturn(record("500.00"));
        when(planMapper.selectByContractAndPeriodForUpdate(1L, 2)).thenReturn(settledPlan);

        BizException ex = assertThrows(BizException.class,
                () -> service.settleByPeriod(record("500.00"), 2));

        assertEquals("PM-003", ex.getCode());
        assertTrue(ex.getMessage().contains("第 2 期已结清"));
        verify(settlementMapper, never()).insert(any());
        verify(recordMapper, never()).updateById(any());
    }

    @Test
    void should_reject_reversed_record() {
        PaymentRecord reversed = record("1000.00");
        reversed.setStatus("REVERSED");
        when(recordMapper.selectForUpdate(100L)).thenReturn(reversed);

        BizException ex = assertThrows(BizException.class,
                () -> service.settleByPeriod(reversed, 1));

        assertEquals("PM-005", ex.getCode());
        verifyNoInteractions(planMapper, settlementMapper);
    }

    @Test
    void should_reject_red_record() {
        PaymentRecord red = record("1000.00");
        red.setStatus("RED");
        when(recordMapper.selectForUpdate(100L)).thenReturn(red);

        BizException ex = assertThrows(BizException.class,
                () -> service.settleByPeriod(red, 1));

        assertEquals("PM-005", ex.getCode());
        verifyNoInteractions(planMapper, settlementMapper);
    }
}
