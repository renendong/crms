package com.company.crms.payment.service;

import com.company.crms.common.response.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 回款计划 Excel 批量导入。
 *
 * <p>与逐行导入不同：计划合计是合同级约束，因此按「合同」整组原子处理，
 * 组内合计必须恰好等于合同金额，任一不满足则该组全部失败。
 */
public interface PaymentPlanImportService {

    ImportResultVO importPlans(MultipartFile file);

    String[] getTemplateHeaders();
}
