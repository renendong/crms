package com.company.crms.customer.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.customer.dto.CreateCustomerDTO;
import com.company.crms.customer.service.CustomerImportService;
import com.company.crms.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerImportServiceImpl implements CustomerImportService {

    private static final String USCC_PATTERN = "^[0-9A-HJ-NPQRTUWXY]{18}$";

    private final CustomerService customerService;

    @Override
    @OperationLog(module = "客户", action = "批量导入", type = "CREATE", recordParams = false)
    public ImportResultVO importCustomers(MultipartFile file) {
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
                        log.warn("[customer-import] row {} failed: {}", rowIndex, ex.getMessage());
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
        String uscc = trim(row.get(2));
        String level = parseLevel(trim(row.get(3)));
        String industry = trim(row.get(4));
        String regionCode = trim(row.get(5));
        String address = trim(row.get(6));
        String remark = trim(row.get(7));

        if (name == null) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("客户名称不能超过 100 个字符");
        }
        if (uscc != null) {
            uscc = uscc.toUpperCase();
            if (!uscc.matches(USCC_PATTERN)) {
                throw new IllegalArgumentException("统一社会信用代码格式不正确");
            }
        }
        if (industry != null && industry.length() > 32) {
            throw new IllegalArgumentException("所属行业不能超过 32 个字符");
        }
        if (regionCode != null && regionCode.length() > 12) {
            throw new IllegalArgumentException("地区编码不能超过 12 个字符");
        }
        if (address != null && address.length() > 255) {
            throw new IllegalArgumentException("地址不能超过 255 个字符");
        }

        CreateCustomerDTO dto = new CreateCustomerDTO();
        dto.setName(name);
        dto.setType(type);
        dto.setUscc(uscc);
        dto.setLevel(level);
        dto.setIndustry(industry);
        dto.setRegionCode(regionCode);
        dto.setAddress(address);
        dto.setRemark(remark);
        customerService.create(dto);
    }

    private static String parseType(String s) {
        if (s == null) {
            return "ENTERPRISE";
        }
        return switch (s.toUpperCase()) {
            case "ENTERPRISE", "企业" -> "ENTERPRISE";
            case "GOVERNMENT", "政府", "政府机构" -> "GOVERNMENT";
            case "INDIVIDUAL", "个人" -> "INDIVIDUAL";
            default -> throw new IllegalArgumentException("客户类型仅支持 企业/政府/个人");
        };
    }

    private static String parseLevel(String s) {
        if (s == null) {
            return "C";
        }
        String level = s.toUpperCase();
        if (!"A".equals(level) && !"B".equals(level) && !"C".equals(level)) {
            throw new IllegalArgumentException("客户等级仅支持 A/B/C");
        }
        return level;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
