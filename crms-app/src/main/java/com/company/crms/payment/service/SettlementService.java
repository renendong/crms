package com.company.crms.payment.service;

import com.company.crms.payment.entity.PaymentRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * 自动核销服务（DSS §3.4.1）。
 *
 * <p>核心算法：
 * <ol>
 *   <li>对实际回款记录加行锁；</li>
 *   <li>取合同下未结清的回款计划（按 plan_date 升序）并加行锁；</li>
 *   <li>逐条扣减 unsettled_amount，剩余金额回写 unallocated_amount；</li>
 *   <li>同步更新 plan.status 为 PARTIAL/SETTLED；</li>
 *   <li>插入 payment_settlement 关联记录。</li>
 * </ol>
 *
 * <p>支持手工指定 targetPlanIds（非空时只在该集合内核销），与自动按时间序逻辑互斥。
 */
public interface SettlementService {

    /**
     * 执行核销。
     *
     * @param record 已写入数据库的实际回款记录（status=NORMAL）。
     * @param targetPlanIds 可空。若非空，则仅核销该集合内的计划，仍按 planDate 升序处理。
     * @return 实际核销总额。
     */
    BigDecimal settle(PaymentRecord record, List<Long> targetPlanIds);

    /**
     * 按「合同 + 期数」定位唯一回款计划并核销。
     *
     * <p>期数必填；金额不足该期待核销额时允许部分核销，差额回写 {@code payment_record.unallocated_amount}，
     * 不跨期外溢。
     *
     * @param record 已写入数据库的实际回款记录（status=NORMAL）
     * @param periodNo 回款计划期数
     * @return 实际核销金额
     */
    BigDecimal settleByPeriod(PaymentRecord record, Integer periodNo);
}
