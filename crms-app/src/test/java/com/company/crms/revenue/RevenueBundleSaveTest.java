package com.company.crms.revenue;

import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.revenue.dto.CreateRevenueDTO;
import com.company.crms.revenue.dto.RevenueBundleDTO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import com.company.crms.revenue.service.RevenueRecordService;
import com.company.crms.revenue.service.impl.RevenueBundleServiceImpl;
import com.company.crms.revenue.vo.RevenueSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 收入与回款计划整批同录（bundle）单测：
 * 计划先落库、再校验、最后写收入；任一校验失败则不写收入。
 */
@ExtendWith(MockitoExtension.class)
class RevenueBundleSaveTest {

    @Mock RevenueRecordMapper revenueMapper;
    @Mock ContractMapper contractMapper;
    @Mock PaymentPlanService planService;
    @Mock RevenueBalanceValidator balanceValidator;
    @Mock RevenueRecordService revenueRecordService;

    @InjectMocks RevenueBundleServiceImpl bundleService;

    private Contract contract() {
        Contract c = new Contract();
        c.setId(1L);
        c.setCode("CT-A");
        c.setAmount(new BigDecimal("10000.00"));
        return c;
    }

    private CreateRevenueDTO revenueDto(long contractId, String amount, LocalDate date) {
        CreateRevenueDTO dto = new CreateRevenueDTO();
        dto.setContractId(contractId);
        dto.setPeriodMonth("2026-04");
        dto.setRecognizeDate(date);
        dto.setIncomeType("SERVICE");
        dto.setAmount(new BigDecimal(amount));
        dto.setTaxRate(new BigDecimal("0.13"));
        dto.setCostAmount(BigDecimal.ZERO);
        return dto;
    }

    private BatchPlanDTO.BatchPlanItem planItem(int periodNo, String amount) {
        BatchPlanDTO.BatchPlanItem item = new BatchPlanDTO.BatchPlanItem();
        item.setPeriodNo(periodNo);
        item.setPlanDate(LocalDate.of(2026, periodNo, 1));
        item.setPlanAmount(new BigDecimal(amount));
        return item;
    }

    private RevenueBundleDTO bundle(List<CreateRevenueDTO> revenues, List<BatchPlanDTO.BatchPlanItem> plans,
                                    boolean overwrite) {
        RevenueBundleDTO dto = new RevenueBundleDTO();
        dto.setContractId(1L);
        dto.setRevenues(revenues);
        dto.setPlans(plans);
        dto.setPlanOverwrite(overwrite);
        return dto;
    }

    @Test
    void should_save_plans_then_revenues_and_return_summary() {
        RevenueSummaryVO summary = new RevenueSummaryVO();
        when(contractMapper.selectById(1L)).thenReturn(contract());
        when(planService.saveForContract(eq(1L), anyList(), eq(true))).thenReturn(List.of(1L, 2L));
        when(revenueRecordService.summary(1L)).thenReturn(summary);

        RevenueBundleDTO dto = bundle(
                List.of(revenueDto(1L, "6000.00", LocalDate.of(2026, 4, 1)),
                        revenueDto(1L, "4000.00", LocalDate.of(2026, 5, 1))),
                List.of(planItem(1, "5000.00"), planItem(2, "5000.00")),
                true);

        RevenueSummaryVO result = bundleService.saveBundle(dto);

        assertSame(summary, result);
        InOrder order = inOrder(planService, balanceValidator, revenueMapper);
        order.verify(planService).saveForContract(eq(1L), anyList(), eq(true));
        order.verify(balanceValidator).validateContract(eq(1L), anyList(), isNull());
        order.verify(revenueMapper, times(2)).insert(any(RevenueRecord.class));
    }

    @Test
    void should_reject_when_plan_total_mismatch_before_writing_revenue() {
        when(contractMapper.selectById(1L)).thenReturn(contract());
        doThrow(new BizException(ErrorCode.PM_PLAN_AMOUNT_MISMATCH,
                "回款计划合计 9000.00 与合同金额 10000.00 不一致，差额 1000.00"))
                .when(planService).saveForContract(eq(1L), anyList(), eq(true));

        RevenueBundleDTO dto = bundle(
                List.of(revenueDto(1L, "10000.00", LocalDate.of(2026, 4, 1))),
                List.of(planItem(1, "9000.00")),
                true);

        BizException ex = assertThrows(BizException.class, () -> bundleService.saveBundle(dto));

        assertEquals("PM-002", ex.getCode());
        verifyNoInteractions(balanceValidator, revenueMapper);
    }

    @Test
    void should_write_plan_but_not_revenue_when_balance_check_fails() {
        when(contractMapper.selectById(1L)).thenReturn(contract());
        when(planService.saveForContract(eq(1L), anyList(), eq(false))).thenReturn(List.of(1L));
        doThrow(new BizException(ErrorCode.RV_AMOUNT_MISMATCH,
                "同一合同含税收入合计 9000.00 与合同金额 10000.00 不一致，差额 1000.00"))
                .when(balanceValidator).validateContract(eq(1L), anyList(), isNull());

        RevenueBundleDTO dto = bundle(
                List.of(revenueDto(1L, "9000.00", LocalDate.of(2026, 4, 1))),
                List.of(planItem(1, "10000.00")),
                false);

        BizException ex = assertThrows(BizException.class, () -> bundleService.saveBundle(dto));

        assertEquals("RV-002", ex.getCode());
        InOrder order = inOrder(planService, balanceValidator);
        order.verify(planService).saveForContract(eq(1L), anyList(), eq(false));
        order.verify(balanceValidator).validateContract(eq(1L), anyList(), isNull());
        verifyNoInteractions(revenueMapper);
        // 整个 bundle 在一个事务内，校验失败时已写入的计划会随事务回滚
        assertTrue(isTransactional());
    }

    @Test
    void should_reject_when_revenue_contract_differs_from_bundle_contract() {
        when(contractMapper.selectById(1L)).thenReturn(contract());

        RevenueBundleDTO dto = bundle(
                List.of(revenueDto(2L, "10000.00", LocalDate.of(2026, 4, 1))),
                null,
                false);

        BizException ex = assertThrows(BizException.class, () -> bundleService.saveBundle(dto));

        assertEquals("VAL-400", ex.getCode());
        assertTrue(ex.getMessage().contains("必须与整批录入的合同一致"));
        verifyNoInteractions(planService, balanceValidator, revenueMapper);
    }

    private static boolean isTransactional() {
        try {
            return RevenueBundleServiceImpl.class
                    .getMethod("saveBundle", RevenueBundleDTO.class)
                    .isAnnotationPresent(Transactional.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
