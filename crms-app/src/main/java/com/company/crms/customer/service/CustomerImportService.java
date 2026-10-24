package com.company.crms.customer.service;

import com.company.crms.common.response.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerImportService {

    /**
     * 通过 Excel 批量导入客户档案。
     *
     * <p>列顺序：客户名称 / 客户类型 / 统一社会信用代码 / 客户等级 /
     * 所属行业 / 地区编码 / 地址 / 备注。第一行为表头。
     *
     * <p>逐行独立事务：单行失败只记录错误并跳过，不影响其它行。
     */
    ImportResultVO importCustomers(MultipartFile file);
}
