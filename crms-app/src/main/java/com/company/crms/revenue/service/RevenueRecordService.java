package com.company.crms.revenue.service;

import com.company.crms.common.response.PageResult;
import com.company.crms.revenue.dto.CreateRevenueDTO;
import com.company.crms.revenue.dto.RevenueQuery;
import com.company.crms.revenue.dto.UpdateRevenueDTO;
import com.company.crms.revenue.vo.RevenueRecordVO;
import com.company.crms.revenue.vo.RevenueSummaryVO;

public interface RevenueRecordService {

    PageResult<RevenueRecordVO> page(RevenueQuery query);

    RevenueRecordVO detail(Long id);

    Long create(CreateRevenueDTO dto);

    void update(Long id, UpdateRevenueDTO dto);

    void remove(Long id);

    /** 合同级收入校验面板：合同金额 / 已确认收入 / 已回款 / 后续计划待收 / 平衡差额。 */
    RevenueSummaryVO summary(Long contractId);
}
