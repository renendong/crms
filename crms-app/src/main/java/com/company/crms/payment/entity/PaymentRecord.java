package com.company.crms.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.crms.common.annotation.SensitiveField;
import com.company.crms.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
public class PaymentRecord extends BaseEntity {
    private Long contractId;
    /** 关联回款计划期数：登记回款时必填，用于按「合同 + 期数」定位计划核销。 */
    private Integer periodNo;
    private LocalDate arrivalDate;
    private BigDecimal amount;
    private String payer;

    @SensitiveField(mask = SensitiveField.Mask.NONE)
    private String voucherNo;

    private String status;
    private Long redRefId;
    private BigDecimal unallocatedAmount;
    private String remark;
    private Long voucherFileId;
}
