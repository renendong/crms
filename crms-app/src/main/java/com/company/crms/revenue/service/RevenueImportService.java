package com.company.crms.revenue.service;

import com.company.crms.common.response.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 收入明细 Excel 批量导入（D8：按合同整组原子处理）。
 */
public interface RevenueImportService {

    /**
     * 列：合同编号 / 收入归属期间 / 确认日期 / 收入类型 / 含税金额 / 税率 / 成本金额 / 单据号 / 备注。
     *
     * <p>同一合同的全部行作为一个整体校验（计划完整 → 时点平衡 → 收入合计 = 合同金额），
     * 组内任一条件不满足则整组失败。
     */
    ImportResultVO importRevenues(MultipartFile file);

    /** 返回导入模板表头列名。 */
    String[] getTemplateHeaders();
}
