package com.company.crms.revenue.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RevenueRecordVO implements Serializable {
    private Long id;
    private Long contractId;
    private String contractCode;
    private String contractName;
    private String customerName;
    private String periodMonth;
    private LocalDate recognizeDate;
    private String incomeType;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal costAmount;
    private BigDecimal grossProfit;
    private String voucherNo;
    private String remark;
    private Integer version;
}
