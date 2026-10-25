package com.company.crms.revenue.service.impl;

import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.mapper.PaymentRecordMapper;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.payment.vo.PaymentPlanSummaryVO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueBalanceValidatorImpl implements RevenueBalanceValidator {

    private final ContractMapper contractMapper;
    private final RevenueRecordMapper revenueMapper;
    private final PaymentRecordMapper recordMapper;
    private final PaymentPlanMapper planMapper;
    private final PaymentPlanService planService;

    @Override
    public void validateContract(Long contractId, List<RevenueRecord> incoming, Long excludeRevenueId) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null) {
            throw new BizException(ErrorCode.CT_NOT_FOUND);
        }

        // 1) 回款计划合计必须等于合同金额（收入确认前先把计划排完整）
        PaymentPlanSummaryVO planSummary = planService.summary(contractId);
        if (planSummary.getPlanCount() == 0) {
            throw new BizException(ErrorCode.RV_PLAN_REQUIRED);
        }
        if (!planSummary.isComplete()) {
            throw new BizException(ErrorCode.PM_PLAN_AMOUNT_MISMATCH,
                    "回款计划合计 " + planSummary.getPlannedAmount().toPlainString()
                            + " 与合同金额 " + planSummary.getContractAmount().toPlainString()
                            + " 不一致，差额 " + planSummary.getDiff().toPlainString()
                            + "，请先补全回款计划");
        }

        // 2) 逐条时点平衡：按确认日期升序累计校验，保证每个时点都成立
        List<RevenueRecord> sorted = new ArrayList<>(incoming);
        sorted.sort(Comparator.comparing(RevenueRecord::getRecognizeDate));
        BigDecimal batchCum = BigDecimal.ZERO;
        for (RevenueRecord r : sorted) {
            LocalDate t = r.getRecognizeDate();
            batchCum = batchCum.add(r.getAmount());
            BigDecimal income = revenueMapper.sumAmountUpTo(contractId, t, excludeRevenueId).add(batchCum);
            BigDecimal received = recordMapper.sumEffectiveAmountUpTo(contractId, t);
            BigDecimal futurePlan = planMapper.sumUnsettledAfter(contractId, t);
            BigDecimal right = received.add(futurePlan);
            if (income.compareTo(right) != 0) {
                throw new BizException(ErrorCode.RV_BALANCE_MISMATCH,
                        "确认日期 " + t + " 处不满足时点平衡：含税收入累计 " + income.toPlainString()
                                + " ≠ 已回款 " + received.toPlainString()
                                + " + 后续计划待收 " + futurePlan.toPlainString()
                                + "（差额 " + income.subtract(right).toPlainString()
                                + "）；公式为「截至该时点的含税收入合计 = 该时点前实际回款 + 该时点后计划待收」");
            }
        }

        // 3) 合同级硬校验：同一合同含税收入合计必须恰好等于合同金额
        BigDecimal incomingSum = incoming.stream()
                .map(RevenueRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = revenueMapper.sumAmountByContract(contractId, excludeRevenueId).add(incomingSum);
        if (total.compareTo(c.getAmount()) != 0) {
            throw new BizException(ErrorCode.RV_AMOUNT_MISMATCH,
                    "同一合同含税收入合计 " + total.toPlainString() + " 与合同金额 "
                            + c.getAmount().toPlainString() + " 不一致，差额 "
                            + c.getAmount().subtract(total).toPlainString());
        }
        log.debug("[revenue] contract {} balance validated, total={}", contractId, total.toPlainString());
    }
}
