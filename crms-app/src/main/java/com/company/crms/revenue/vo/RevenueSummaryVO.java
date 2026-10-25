package com.company.crms.revenue.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合同级收入校验面板。
 *
 * <p>contractDiff：合同金额 - 已确认收入合计，必须为 0；
 * balanceDiff：已确认收入 - (已回款 + 后续计划待收)，时点平衡差额，必须为 0。
 */
@Data
public class RevenueSummaryVO implements Serializable {
    private Long contractId;
    private String contractCode;
    private String contractName;
    private BigDecimal contractAmount;
    private BigDecimal confirmedAmount;
    private BigDecimal contractDiff;
    private BigDecimal receivedAmount;
    private BigDecimal plannedUnsettled;
    private BigDecimal balanceDiff;
    private boolean complete;
}
