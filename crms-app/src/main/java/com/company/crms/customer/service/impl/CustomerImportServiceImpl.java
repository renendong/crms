package com.company.crms.customer.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.customer.dto.CreateCustomerDTO;
import com.company.crms.customer.service.CustomerImportService;
import com.company.crms.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerImportServiceImpl implements CustomerImportService {

    private static final String USCC_PATTERN = "^[0-9A-HJ-NPQRTUWXY]{18}$";

    private static final String[] HEADERS = {
            "客户名称", "客户类型", "统一社会信用代码", "客户等级", "所属行业", "地区编码", "地址", "备注"
    };

    private final CustomerService customerService;

    @Override
    @OperationLog(module = "客户", action = "批量导入", type = "CREATE", recordParams = false)
    public ImportResultVO importCustomers(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        Set<String> seenUscc = new HashSet<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {

                private boolean headerValid = false;
                private boolean headerChecked = false;

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    ExcelImportHelper.validateHeader(HEADERS, headMap, "客户");
                    headerValid = true;
                    headerChecked = true;
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                    if (!headerChecked) {
                        // 某些情况下 EasyExcel 不会触发 invokeHeadMap，此处兜底
                        ExcelImportHelper.validateHeader(HEADERS, row, "客户");
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
                        importRow(row, seenUscc);
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
        } catch (RuntimeException e) {
            // 表头校验等整体失败：把错误信息写入回执，避免被全局异常屏蔽
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

    private void importRow(Map<Integer, String> row, Set<String> seenUscc) {
        String name = ExcelImportHelper.trim(row.get(0));
        String type = parseType(ExcelImportHelper.trim(row.get(1)));
        String uscc = ExcelImportHelper.trim(row.get(2));
        String level = parseLevel(ExcelImportHelper.trim(row.get(3)));
        String industry = ExcelImportHelper.trim(row.get(4));
        String regionCode = ExcelImportHelper.trim(row.get(5));
        String address = ExcelImportHelper.trim(row.get(6));
        String remark = ExcelImportHelper.trim(row.get(7));

        if (name == null) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        ExcelImportHelper.checkLength(name, 100, "客户名称");
        if (uscc != null) {
            uscc = uscc.toUpperCase();
            if (!uscc.matches(USCC_PATTERN)) {
                throw new IllegalArgumentException("统一社会信用代码格式不正确");
            }
            if (!seenUscc.add(uscc)) {
                throw new IllegalArgumentException("文件内已存在相同的统一社会信用代码：" + uscc);
            }
        }
        ExcelImportHelper.checkLength(industry, 32, "所属行业");
        ExcelImportHelper.checkLength(regionCode, 12, "地区编码");
        ExcelImportHelper.checkLength(address, 255, "地址");

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
}
