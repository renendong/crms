package com.company.crms.payment.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合同级回款计划汇总：用于「计划合计必须等于合同金额」的缺口提示。
 */
@Data
public class PaymentPlanSummaryVO implements Serializable {
    private Long contractId;
    private String contractCode;
    private String contractName;
    private BigDecimal contractAmount;
    private BigDecimal plannedAmount;
    /** 合同金额 - 已排计划合计，为 0 表示计划完整。 */
    private BigDecimal diff;
    private long planCount;
    private boolean complete;
}
