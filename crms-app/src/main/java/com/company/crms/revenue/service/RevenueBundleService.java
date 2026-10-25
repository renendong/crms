package com.company.crms.revenue.service;

import com.company.crms.revenue.dto.RevenueBundleDTO;
import com.company.crms.revenue.vo.RevenueSummaryVO;

/**
 * 收入整批录入：收入明细与回款计划在同一事务内一起提交。
 *
 * <p>硬校验要求「同一合同含税收入合计 = 合同金额」，逐条录入无法满足该等式，
 * 故提供整批入口，由前端一次性提交该合同的全部收入与回款计划。
 */
public interface RevenueBundleService {

    /**
     * 保存整批收入（可选同时保存回款计划）。
     *
     * @return 保存后该合同的收入校验面板
     */
    RevenueSummaryVO saveBundle(RevenueBundleDTO dto);
}
