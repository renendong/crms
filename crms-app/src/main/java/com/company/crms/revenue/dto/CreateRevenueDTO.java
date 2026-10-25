package com.company.crms.revenue.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateRevenueDTO {

    @NotNull
    private Long contractId;

    /** 收入归属期间，格式 YYYY-MM。 */
    @NotBlank
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "收入归属期间格式应为 YYYY-MM")
    private String periodMonth;

    @NotNull
    private LocalDate recognizeDate;

    /** GOODS / SERVICE / OTHER，空默认 SERVICE。 */
    private String incomeType;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    /** 税率 0~1（如 0.13），空默认 0。 */
    @DecimalMin(value = "0", message = "税率不能小于 0")
    @DecimalMax(value = "1", message = "税率不能大于 1")
    private BigDecimal taxRate;

    @DecimalMin(value = "0", message = "成本金额不能小于 0")
    private BigDecimal costAmount;

    @Size(max = 255)
    private String voucherNo;

    @Size(max = 500)
    private String remark;
}
