package com.company.crms.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateRecordDTO {
    @NotNull
    private Long contractId;

    /** 回款计划期数，必填：按「合同 + 期数」定位唯一计划核销。 */
    @NotNull
    @Positive
    private Integer periodNo;

    @NotNull
    private LocalDate arrivalDate;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @Size(max = 100)
    private String payer;

    @Size(max = 255)
    private String voucherNo;

    @Size(max = 500)
    private String remark;

    private Long voucherFileId;
}
