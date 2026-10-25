package com.company.crms.payment.service;

import com.company.crms.common.response.PageResult;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.dto.CreatePlanDTO;
import com.company.crms.payment.dto.GeneratePlansDTO;
import com.company.crms.payment.dto.PlanQuery;
import com.company.crms.payment.dto.UpdatePlanDTO;
import com.company.crms.payment.vo.PaymentPlanSummaryVO;
import com.company.crms.payment.vo.PaymentPlanVO;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentPlanService {

    PageResult<PaymentPlanVO> page(PlanQuery query);

    List<PaymentPlanVO> listByContract(Long contractId);

    PaymentPlanVO detail(Long id);

    Long create(CreatePlanDTO dto);

    /** 根据合同金额按频率（MONTHLY / QUARTERLY / ONCE）批量生成计划。 */
    List<Long> generate(GeneratePlansDTO dto);

    void update(Long id, UpdatePlanDTO dto);

    void remove(Long id);

    /** 批量录入：整批写入某合同的回款计划，合计必须恰好等于合同金额。 */
    List<Long> batchSave(BatchPlanDTO dto);

    /**
     * 合并保存某个合同的回款计划（供「收入确认同录回款计划」复用）。
     *
     * @param overwrite true=整批覆盖（物理删除后重建）；false=按期数 upsert
     * @return 落库的计划 id 列表
     */
    List<Long> saveForContract(Long contractId, List<BatchPlanDTO.BatchPlanItem> plans, boolean overwrite);

    /** 合同级计划汇总（合同金额 / 已排合计 / 差额 / 是否完整）。 */
    PaymentPlanSummaryVO summary(Long contractId);

    /**
     * 硬校验：该合同未删除计划金额合计（排除 excludePlanId，加上 incoming）必须恰好等于合同金额。
     *
     * @param incoming 本次新增/变更的计划金额，可为 null（按 0 处理）
     */
    void assertTotalEqualsContract(Long contractId, Long excludePlanId, BigDecimal incoming);

    /** 标记逾期：plan_date < today 且未结清。返回受影响行数。 */
    int markOverdueAuto();
}
