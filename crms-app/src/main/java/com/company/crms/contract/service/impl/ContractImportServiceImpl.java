package com.company.crms.contract.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractImportServiceImpl implements ContractImportService {

    private final ContractService contractService;
    private final CustomerMapper customerMapper;

    @Override
    @OperationLog(module = "合同", action = "批量导入", type = "CREATE", recordParams = false)
    public ImportResultVO importContracts(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
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
        }
        return result;
    }

    private void importRow(Map<Integer, String> row) {
        String name = trim(row.get(0));
        String type = parseType(trim(row.get(1)));
        String customerName = trim(row.get(2));
        String amountStr = trim(row.get(3));
        String signedAtStr = trim(row.get(4));
        String performStartStr = trim(row.get(5));
        String performEndStr = trim(row.get(6));
        String remindDaysStr = trim(row.get(7));
        String remark = trim(row.get(8));

        if (name == null) {
            throw new IllegalArgumentException("合同名称不能为空");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("合同名称不能超过 100 个字符");
        }
        if (customerName == null) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        BigDecimal amount = parseAmount(amountStr);
        LocalDate signedAt = parseDate(signedAtStr, "签订日期");
        LocalDate performStartAt = parseDate(performStartStr, "履约开始日期");
        LocalDate performEndAt = parseDate(performEndStr, "履约结束日期");
        if (performEndAt.isBefore(performStartAt)) {
            throw new IllegalArgumentException("履约结束日期必须不早于开始日期");
        }

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

    private static LocalDate parseDate(String s, String label) {
        if (s == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/M/d"));
            } catch (Exception e2) {
                throw new IllegalArgumentException(label + "格式错误，请用 yyyy-MM-dd");
            }
        }
    }

    private static BigDecimal parseAmount(String s) {
        if (s == null) {
            throw new IllegalArgumentException("合同金额不能为空");
        }
        try {
            BigDecimal v = new BigDecimal(s.replace(",", ""));
            if (v.signum() <= 0) {
                throw new IllegalArgumentException("合同金额必须大于 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("合同金额格式错误");
        }
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

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
