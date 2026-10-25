package com.company.crms.revenue.service;

import com.company.crms.revenue.entity.RevenueRecord;

import java.util.List;

/**
 * 收入确认联动校验：回款计划完整性 + 时点平衡公式 + 合同级收入合计。
 *
 * <p>校验口径（T = 收入确认日期）：
 * <pre>
 * INCOME(≤T) == RECEIVED(≤T) + PLAN_UNSETTLED(>T)
 * </pre>
 * 且同一合同收入合计必须恰好等于合同金额。
 */
public interface RevenueBalanceValidator {

    /**
     * @param contractId      合同 id
     * @param incoming        本次待写入的收入明细（单条新增时只含一条；整批录入时含全部行）
     * @param excludeRevenueId 编辑场景下需要排除的已有记录 id，新增传 null
     */
    void validateContract(Long contractId, List<RevenueRecord> incoming, Long excludeRevenueId);
}
