package com.company.crms.revenue.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RevenueQuery {
    private Long contractId;
    private String periodMonth;
    private String incomeType;
    private String voucherNo;
    private LocalDate fromDate;
    private LocalDate toDate;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    private Integer size = 20;
}
