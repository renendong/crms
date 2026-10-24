package com.company.crms.contract.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.contract.dto.CreateContractDTO;
import com.company.crms.contract.service.ContractImportService;
import com.company.crms.contract.service.ContractService;
import com.company.crms.customer.entity.Customer;
import com.company.crms.customer.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractImportServiceImpl implements ContractImportService {

    private static final String[] HEADERS = {
            "合同名称", "合同类型", "客户名称", "合同金额", "签订日期",
            "履约开始日期", "履约结束日期", "提醒天数", "备注"
    };

    private final ContractService contractService;
    private final CustomerMapper customerMapper;

    @Override
    @OperationLog(module = "合同", action = "批量导入", type = "CREATE", recordParams = false)
    public ImportResultVO importContracts(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {

                private boolean headerValid = false;
                private boolean headerChecked = false;

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    ExcelImportHelper.validateHeader(HEADERS, headMap, "合同");
                    headerValid = true;
                    headerChecked = true;
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                    if (!headerChecked) {
                        ExcelImportHelper.validateHeader(HEADERS, row, "合同");
                        headerChecked = true;
                        return;
                    }
                    if (!headerValid) {
                        return;
                    }
                    if (ExcelImportHelper.isEmptyRow(row)) {
                        return;
                    }
                    if (result.getTotal() >= ExcelImportHelper.MAX_ROWS) {
                        throw new IllegalArgumentException("单次导入不能超过 " + ExcelImportHelper.MAX_ROWS + " 行");
                    }
                    int rowIndex = ctx.readRowHolder().getRowIndex() + 1;
                    result.setTotal(result.getTotal() + 1);
                    try {
                        importRow(row);
                        result.setSuccess(result.getSuccess() + 1);
                    } catch (Exception ex) {
                        result.setFailed(result.getFailed() + 1);
                        result.getErrors().add(new ImportResultVO.RowError(rowIndex, ex.getMessage()));
                        log.warn("[contract-import] row {} failed: {}", rowIndex, ex.getMessage());
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                    /* nothing */
                }
            }).headRowNumber(1).sheet().doRead();
        } catch (IOException e) {
            throw new RuntimeException("导入失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (result.getTotal() == 0 && result.getSuccess() == 0 && result.getFailed() == 0) {
                result.setFailed(1);
                result.getErrors().add(new ImportResultVO.RowError(0, unwrapMessage(e)));
            } else {
                throw e;
            }
        }
        return result;
    }

    private static String unwrapMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getMessage() : t.getMessage();
    }

    @Override
    public String[] getTemplateHeaders() {
        return HEADERS;
    }

    private void importRow(Map<Integer, String> row) {
        String name = ExcelImportHelper.trim(row.get(0));
        String type = parseType(ExcelImportHelper.trim(row.get(1)));
        String customerName = ExcelImportHelper.trim(row.get(2));
        String amountStr = ExcelImportHelper.trim(row.get(3));
        String signedAtStr = ExcelImportHelper.trim(row.get(4));
        String performStartStr = ExcelImportHelper.trim(row.get(5));
        String performEndStr = ExcelImportHelper.trim(row.get(6));
        String remindDaysStr = ExcelImportHelper.trim(row.get(7));
        String remark = ExcelImportHelper.trim(row.get(8));

        if (name == null) {
            throw new IllegalArgumentException("合同名称不能为空");
        }
        ExcelImportHelper.checkLength(name, 100, "合同名称");
        if (customerName == null) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        BigDecimal amount = ExcelImportHelper.parseAmount(amountStr, "合同金额");
        LocalDate signedAt = ExcelImportHelper.parseDateFlexible(signedAtStr, "签订日期");
        LocalDate performStartAt = ExcelImportHelper.parseDateFlexible(performStartStr, "履约开始日期");
        LocalDate performEndAt = ExcelImportHelper.parseDateFlexible(performEndStr, "履约结束日期");
        if (performEndAt.isBefore(performStartAt)) {
            throw new IllegalArgumentException("履约结束日期必须不早于开始日期");
        }
        ExcelImportHelper.checkLength(remark, 1000, "备注");

        List<Customer> customers = customerMapper.selectList(
                new QueryWrapper<Customer>().eq("name", customerName));
        if (customers.isEmpty()) {
            throw new IllegalArgumentException("客户 [" + customerName + "] 不存在");
        }
        if (customers.size() > 1) {
            throw new IllegalArgumentException("客户名称 [" + customerName + "] 存在多条记录，请先合并或改用唯一客户");
        }

        CreateContractDTO dto = new CreateContractDTO();
        dto.setName(name);
        dto.setType(type);
        dto.setCustomerId(customers.get(0).getId());
        dto.setAmount(amount);
        dto.setSignedAt(signedAt);
        dto.setPerformStartAt(performStartAt);
        dto.setPerformEndAt(performEndAt);
        dto.setRemindDays(parseInt(remindDaysStr));
        dto.setRemark(remark);
        contractService.create(dto);
    }

    private static String parseType(String s) {
        if (s == null) {
            return "SALES";
        }
        return switch (s.toUpperCase()) {
            case "SALES", "销售" -> "SALES";
            case "PROCUREMENT", "采购" -> "PROCUREMENT";
            case "SERVICE", "服务" -> "SERVICE";
            case "OTHER", "其他" -> "OTHER";
            default -> throw new IllegalArgumentException("合同类型仅支持 销售/采购/服务/其他");
        };
    }

    private static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("提醒天数必须为整数");
        }
    }
}
