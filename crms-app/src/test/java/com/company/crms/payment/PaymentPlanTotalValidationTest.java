package com.company.crms.payment;

import com.company.crms.common.exception.BizException;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.dto.CreatePlanDTO;
import com.company.crms.payment.dto.UpdatePlanDTO;
import com.company.crms.payment.entity.PaymentPlan;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.service.impl.PaymentPlanServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回款计划「合计必须恰好等于合同金额」硬校验单测。
 *
 * <p>覆盖单行新增 / 编辑、整批覆盖录入、非覆盖 upsert 与已核销保护。
 */
@ExtendWith(MockitoExtension.class)
class PaymentPlanTotalValidationTest {

    @Mock PaymentPlanMapper planMapper;
    @Mock ContractMapper contractMapper;

    @InjectMocks PaymentPlanServiceImpl planService;

    private Contract contract(long id, String amount) {
        Contract c = new Contract();
        c.setId(id);
        c.setCode("CT-A");
        c.setName("测试合同");
        c.setAmount(new BigDecimal(amount));
        c.setRemindDays(7);
        return c;
    }

    private CreatePlanDTO createDto(int periodNo, String amount) {
        CreatePlanDTO dto = new CreatePlanDTO();
        dto.setContractId(1L);
        dto.setPeriodNo(periodNo);
        dto.setPlanDate(LocalDate.of(2026, 1, 1));
        dto.setPlanAmount(new BigDecimal(amount));
        return dto;
    }

    private BatchPlanDTO.BatchPlanItem item(int periodNo, String amount) {
        BatchPlanDTO.BatchPlanItem i = new BatchPlanDTO.BatchPlanItem();
        i.setPeriodNo(periodNo);
        i.setPlanDate(LocalDate.of(2026, 1, 1));
        i.setPlanAmount(new BigDecimal(amount));
        return i;
    }

    private BatchPlanDTO batch(boolean overwrite, BatchPlanDTO.BatchPlanItem... items) {
        BatchPlanDTO dto = new BatchPlanDTO();
        dto.setContractId(1L);
        dto.setOverwrite(overwrite);
        dto.setPlans(List.of(items));
        return dto;
    }

    // ------------------------------------------------------------------
    // 单行新增 / 编辑
    // ------------------------------------------------------------------

    @Test
    void create_should_pass_when_total_equals_contract_amount() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectByContractAndPeriod(1L, 3)).thenReturn(null);
        when(planMapper.sumPlanAmountExcluding(1L, null)).thenReturn(new BigDecimal("6000.00"));

        planService.create(createDto(3, "4000.00"));

        verify(planMapper).insert(any(PaymentPlan.class));
    }

    @Test
    void create_should_reject_when_total_is_less_than_contract_amount() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectByContractAndPeriod(1L, 3)).thenReturn(null);
        when(planMapper.sumPlanAmountExcluding(1L, null)).thenReturn(new BigDecimal("5000.00"));

        BizException ex = assertThrows(BizException.class,
                () -> planService.create(createDto(3, "4000.00")));

        assertEquals("PM-002", ex.getCode());
        assertTrue(ex.getMessage().contains("差额 1000.00"));
        verify(planMapper, never()).insert(any());
    }

    @Test
    void create_should_reject_when_total_exceeds_contract_amount() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectByContractAndPeriod(1L, 3)).thenReturn(null);
        when(planMapper.sumPlanAmountExcluding(1L, null)).thenReturn(new BigDecimal("7000.00"));

        BizException ex = assertThrows(BizException.class,
                () -> planService.create(createDto(3, "4000.00")));

        assertEquals("PM-002", ex.getCode());
        assertTrue(ex.getMessage().contains("差额 -1000.00"));
        verify(planMapper, never()).insert(any());
    }

    @Test
    void create_should_reject_duplicate_period() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectByContractAndPeriod(1L, 1)).thenReturn(new PaymentPlan());

        BizException ex = assertThrows(BizException.class,
                () -> planService.create(createDto(1, "4000.00")));

        assertEquals("PM-007", ex.getCode());
        assertTrue(ex.getMessage().contains("第 1 期已存在"));
        verify(planMapper, never()).insert(any());
    }

    @Test
    void update_should_recount_total_excluding_edited_plan() {
        PaymentPlan existing = new PaymentPlan();
        existing.setId(9L);
        existing.setContractId(1L);
        existing.setPeriodNo(1);
        existing.setPlanAmount(new BigDecimal("4000.00"));
        existing.setSettledAmount(BigDecimal.ZERO);
        existing.setUnsettledAmount(new BigDecimal("4000.00"));
        existing.setStatus("PENDING");
        existing.setVersion(2);

        when(planMapper.selectById(9L)).thenReturn(existing);
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.sumPlanAmountExcluding(1L, 9L)).thenReturn(new BigDecimal("4000.00"));
        when(planMapper.updateById(any())).thenReturn(1);

        UpdatePlanDTO dto = new UpdatePlanDTO();
        dto.setPlanDate(LocalDate.of(2026, 2, 1));
        dto.setPlanAmount(new BigDecimal("6000.00"));
        dto.setVersion(2);
        planService.update(9L, dto);

        ArgumentCaptor<PaymentPlan> captor = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(planMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("6000.00"), captor.getValue().getUnsettledAmount());
        assertEquals("PENDING", captor.getValue().getStatus());
    }

    @Test
    void update_should_reject_settled_plan() {
        PaymentPlan existing = new PaymentPlan();
        existing.setId(9L);
        existing.setStatus("SETTLED");

        when(planMapper.selectById(9L)).thenReturn(existing);

        UpdatePlanDTO dto = new UpdatePlanDTO();
        dto.setPlanDate(LocalDate.of(2026, 2, 1));
        dto.setPlanAmount(new BigDecimal("6000.00"));
        dto.setVersion(2);

        BizException ex = assertThrows(BizException.class, () -> planService.update(9L, dto));
        assertEquals("PM-003", ex.getCode());
    }

    // ------------------------------------------------------------------
    // 整批录入
    // ------------------------------------------------------------------

    @Test
    void batch_overwrite_should_pass_when_total_equals_contract_amount() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.physicalDeleteByContract(1L)).thenReturn(2);

        List<Long> ids = planService.batchSave(batch(true,
                item(1, "4000.00"), item(2, "6000.00")));

        assertEquals(2, ids.size());
        verify(planMapper).physicalDeleteByContract(1L);
        verify(planMapper, times(2)).insert(any(PaymentPlan.class));
    }

    @Test
    void batch_should_reject_whole_group_when_total_mismatch() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));

        BizException ex = assertThrows(BizException.class, () -> planService.batchSave(batch(true,
                item(1, "4000.00"), item(2, "5000.00"))));

        assertEquals("PM-002", ex.getCode());
        assertTrue(ex.getMessage().contains("差额 1000.00"));
        verify(planMapper, never()).insert(any());
        verify(planMapper, never()).physicalDeleteByContract(1L);
    }

    @Test
    void batch_should_reject_duplicate_period() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));

        BizException ex = assertThrows(BizException.class, () -> planService.batchSave(batch(true,
                item(1, "4000.00"), item(1, "6000.00"))));

        assertEquals("PM-007", ex.getCode());
        verify(planMapper, never()).insert(any());
    }

    @Test
    void batch_overwrite_should_reject_when_contract_has_settled_plan() {
        PaymentPlan settled = new PaymentPlan();
        settled.setId(9L);
        settled.setContractId(1L);
        settled.setPeriodNo(1);
        settled.setPlanAmount(new BigDecimal("4000.00"));
        settled.setSettledAmount(new BigDecimal("1000.00"));

        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectList(any())).thenReturn(List.of(settled));

        BizException ex = assertThrows(BizException.class, () -> planService.batchSave(batch(true,
                item(1, "4000.00"), item(2, "6000.00"))));

        assertEquals("PM-003", ex.getCode());
        verify(planMapper, never()).physicalDeleteByContract(1L);
    }

    @Test
    void batch_upsert_should_keep_other_periods_and_recount_total() {
        PaymentPlan period1 = new PaymentPlan();
        period1.setId(9L);
        period1.setContractId(1L);
        period1.setPeriodNo(1);
        period1.setPlanAmount(new BigDecimal("4000.00"));
        period1.setSettledAmount(BigDecimal.ZERO);
        period1.setUnsettledAmount(new BigDecimal("4000.00"));
        period1.setStatus("PENDING");
        period1.setVersion(3);

        PaymentPlan period2 = new PaymentPlan();
        period2.setId(10L);
        period2.setContractId(1L);
        period2.setPeriodNo(2);
        period2.setPlanAmount(new BigDecimal("6000.00"));
        period2.setSettledAmount(BigDecimal.ZERO);
        period2.setUnsettledAmount(new BigDecimal("6000.00"));
        period2.setStatus("PENDING");

        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectList(any())).thenReturn(List.of(period1, period2));

        // 第一期由 4000 改为 6000，第二期保留 6000，合计 12000 ≠ 10000 → 拒绝
        BizException ex = assertThrows(BizException.class, () -> planService.batchSave(batch(false,
                item(1, "6000.00"))));
        assertEquals("PM-002", ex.getCode());
    }

    @Test
    void batch_upsert_should_update_existing_period_status_and_unsettled_amount() {
        PaymentPlan period1 = new PaymentPlan();
        period1.setId(9L);
        period1.setContractId(1L);
        period1.setPeriodNo(1);
        period1.setPlanAmount(new BigDecimal("4000.00"));
        period1.setSettledAmount(BigDecimal.ZERO);
        period1.setUnsettledAmount(new BigDecimal("4000.00"));
        period1.setStatus("PENDING");
        period1.setVersion(3);

        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.selectList(any())).thenReturn(List.of(period1));
        when(planMapper.updateById(any())).thenReturn(1);

        // 仅一期，改为 10000 → 合计恰好等于合同金额
        List<Long> ids = planService.batchSave(batch(false, item(1, "10000.00")));

        assertEquals(List.of(9L), ids);
        ArgumentCaptor<PaymentPlan> captor = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(planMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("10000.00"), captor.getValue().getUnsettledAmount());
        assertEquals("PENDING", captor.getValue().getStatus());
        verify(planMapper, never()).insert(any());
    }

    @Test
    void summary_should_report_diff_and_complete_flag() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "10000.00"));
        when(planMapper.sumPlanAmountExcluding(1L, null)).thenReturn(new BigDecimal("6000.00"));
        when(planMapper.countByContract(1L)).thenReturn(2L);

        var vo = planService.summary(1L);

        assertEquals(new BigDecimal("4000.00"), vo.getDiff());
        assertEquals(2L, vo.getPlanCount());
        assertFalse(vo.isComplete());
    }
}
