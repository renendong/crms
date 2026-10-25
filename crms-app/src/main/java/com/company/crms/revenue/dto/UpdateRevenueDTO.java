package com.company.crms.revenue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改收入明细，字段约束同新增，另需乐观锁版本号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRevenueDTO extends CreateRevenueDTO {

    @NotNull
    private Integer version;
}
