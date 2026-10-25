package com.company.crms.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 回款计划批量录入：整批覆盖某个合同的全部回款计划。
 */
@Data
public class BatchPlanDTO {

    @NotNull
    private Long contractId;

    @NotEmpty
    @Valid
    private List<BatchPlanItem> plans;

    /** 是否覆盖已有计划，默认 true。为 false 时按「期数已存在则更新，否则新增」处理。 */
    private Boolean overwrite = Boolean.TRUE;

    @Data
    public static class BatchPlanItem {

        @NotNull
        @Positive
        private Integer periodNo;

        @NotNull
        private LocalDate planDate;

        @NotNull
        @DecimalMin(value = "0.01")
        private BigDecimal planAmount;

        private Integer remindDays;

        @Size(max = 500)
        private String remark;
    }
}
