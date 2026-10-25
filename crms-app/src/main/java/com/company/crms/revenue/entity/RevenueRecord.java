package com.company.crms.revenue.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.crms.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收入确认明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("revenue_record")
public class RevenueRecord extends BaseEntity {
    private Long contractId;
    /** 收入归属期间，YYYY-MM。 */
    private String periodMonth;
    /** 收入确认日期，即时点平衡校验的 T。 */
    private LocalDate recognizeDate;
    /** GOODS / SERVICE / OTHER。 */
    private String incomeType;
    /** 含税收入金额。 */
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal costAmount;
    private BigDecimal grossProfit;
    private String voucherNo;
    private String remark;
}
