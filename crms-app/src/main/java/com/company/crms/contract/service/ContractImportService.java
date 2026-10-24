package com.company.crms.contract.service;

import com.company.crms.common.response.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface ContractImportService {

    /**
     * 通过 Excel 批量导入合同。
     *
     * <p>列顺序：合同名称 / 合同类型 / 客户名称 / 合同金额 / 签订日期 /
     * 履约开始日期 / 履约结束日期 / 提醒天数 / 备注。第一行为表头。
     *
     * <p>合同编号自动生成，初始状态为 DRAFT；逐行独立事务。
     */
    ImportResultVO importContracts(MultipartFile file);

    /** 返回导入模板表头列名，用于生成下载模板。 */
    String[] getTemplateHeaders();
}
