package com.company.crms.revenue.dto;

import com.company.crms.payment.dto.BatchPlanDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 收入确认整批录入：收入明细与回款计划在同一事务内一起提交。
 */
@Data
public class RevenueBundleDTO {

    @NotNull
    private Long contractId;

    @NotEmpty
    @Valid
    private List<CreateRevenueDTO> revenues;

    /** 回款计划（可为空：为空表示沿用已录入的计划）。 */
    @Valid
    private List<BatchPlanDTO.BatchPlanItem> plans;

    /** 为 true 时整批覆盖该合同已有计划（要求无核销记录），默认 false 按期数 upsert。 */
    private Boolean planOverwrite = Boolean.FALSE;
}
