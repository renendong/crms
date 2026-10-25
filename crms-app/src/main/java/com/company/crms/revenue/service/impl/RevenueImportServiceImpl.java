package com.company.crms.revenue.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.revenue.dto.CreateRevenueDTO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import com.company.crms.revenue.service.RevenueImportService;
import com.company.crms.revenue.support.RevenueRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueImportServiceImpl implements RevenueImportService {

    private static final String[] HEADERS = {
            "合同编号", "收入归属期间", "确认日期", "收入类型", "含税金额", "税率", "成本金额", "单据号", "备注"
    };

    private final ContractMapper contractMapper;
    private final RevenueRecordMapper revenueMapper;
    private final RevenueBalanceValidator balanceValidator;
    private final TransactionTemplate transactionTemplate;

    @Override
    @OperationLog(module = "收入确认", action = "批量导入收入", type = "CREATE", recordParams = false)
    public ImportResultVO importRevenues(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        List<RawRow> raws = readRows(file, result);

        // 1) 逐行解析，按合同分组（行级错误即时记入结果）
        Set<String> seenVoucher = new HashSet<>();
        Map<Long, List<ParsedRow>> groups = new LinkedHashMap<>();
        for (RawRow raw : raws) {
            result.setTotal(result.getTotal() + 1);
            try {
                ParsedRow parsed = parseRow(raw, seenVoucher);
                groups.computeIfAbsent(parsed.contractId, k -> new ArrayList<>()).add(parsed);
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(new ImportResultVO.RowError(raw.rowIndex, ex.getMessage()));
                log.warn("[revenue-import] row {} failed: {}", raw.rowIndex, ex.getMessage());
            }
        }

        // 2) 按合同整组校验并写入：计划完整 → 时点平衡 → 收入合计 = 合同金额
        for (Map.Entry<Long, List<ParsedRow>> entry : groups.entrySet()) {
            List<ParsedRow> rows = entry.getValue();
            List<RevenueRecord> records = rows.stream().map(r -> r.record).toList();
            try {
                balanceValidator.validateContract(entry.getKey(), records, null);
                transactionTemplate.executeWithoutResult(status -> records.forEach(revenueMapper::insert));
                result.setSuccess(result.getSuccess() + rows.size());
            } catch (Exception ex) {
                String msg = ex.getMessage();
                for (ParsedRow row : rows) {
                    result.setFailed(result.getFailed() + 1);
                    result.getErrors().add(new ImportResultVO.RowError(row.rowIndex, msg));
                }
                log.warn("[revenue-import] contract {} group failed: {}", entry.getKey(), msg);
            }
        }
        return result;
    }

    @Override
    public String[] getTemplateHeaders() {
        return HEADERS;
    }

    private List<RawRow> readRows(MultipartFile file, ImportResultVO result) {
        List<RawRow> raws = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {

                private boolean headerChecked = false;

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    ExcelImportHelper.validateHeader(HEADERS, headMap, "收入");
                    headerChecked = true;
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                    if (!headerChecked) {
                        ExcelImportHelper.validateHeader(HEADERS, row, "收入");
                        headerChecked = true;
                        return;
                    }
                    if (ExcelImportHelper.isEmptyRow(row)) {
                        return;
                    }
                    if (raws.size() >= ExcelImportHelper.MAX_ROWS) {
                        throw new IllegalArgumentException("单次导入不能超过 " + ExcelImportHelper.MAX_ROWS + " 行");
                    }
                    raws.add(new RawRow(ctx.readRowHolder().getRowIndex() + 1, row));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                    /* nothing */
                }
            }).headRowNumber(1).sheet().doRead();
        } catch (IOException e) {
            throw new RuntimeException("导入失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (raws.isEmpty()) {
                result.setTotal(0);
                result.setFailed(1);
                result.getErrors().add(new ImportResultVO.RowError(0, unwrapMessage(e)));
                return raws;
            }
            throw e;
        }
        return raws;
    }

    private ParsedRow parseRow(RawRow raw, Set<String> seenVoucher) {
        Map<Integer, String> row = raw.row;
        String contractCode = ExcelImportHelper.trim(row.get(0));
        String periodMonth = ExcelImportHelper.trim(row.get(1));
        String recognizeDateStr = ExcelImportHelper.trim(row.get(2));
        String incomeType = ExcelImportHelper.trim(row.get(3));
        String amountStr = ExcelImportHelper.trim(row.get(4));
        String taxRateStr = ExcelImportHelper.trim(row.get(5));
        String costStr = ExcelImportHelper.trim(row.get(6));
        String voucherNo = ExcelImportHelper.trim(row.get(7));
        String remark = ExcelImportHelper.trim(row.get(8));

        if (contractCode == null) {
            throw new IllegalArgumentException("合同编号不能为空");
        }
        Contract c = contractMapper.selectOne(new QueryWrapper<Contract>()
                .eq("code", contractCode)
                .eq("is_deleted", 0));
        if (c == null) {
            throw new IllegalArgumentException("合同 [" + contractCode + "] 不存在");
        }

        LocalDate recognizeDate = ExcelImportHelper.parseDateFlexible(recognizeDateStr, "确认日期");
        BigDecimal amount = ExcelImportHelper.parseAmount(amountStr, "含税金额");
        BigDecimal taxRate = parseRate(taxRateStr);
        BigDecimal costAmount = parseNonNegative(costStr, "成本金额");
        ExcelImportHelper.checkLength(voucherNo, 255, "单据号");
        ExcelImportHelper.checkLength(remark, 500, "备注");
        if (voucherNo != null && !seenVoucher.add(voucherNo)) {
            throw new IllegalArgumentException("文件内已存在相同的单据号：" + voucherNo);
        }

        CreateRevenueDTO dto = new CreateRevenueDTO();
        dto.setContractId(c.getId());
        dto.setPeriodMonth(periodMonth);
        dto.setRecognizeDate(recognizeDate);
        dto.setIncomeType(incomeType);
        dto.setAmount(amount);
        dto.setTaxRate(taxRate);
        dto.setCostAmount(costAmount);
        dto.setVoucherNo(voucherNo);
        dto.setRemark(remark);

        ParsedRow parsed = new ParsedRow();
        parsed.rowIndex = raw.rowIndex;
        parsed.contractId = c.getId();
        parsed.record = RevenueRules.fromDTO(dto);
        return parsed;
    }

    /** 税率：空按 0，范围 [0,1] 且最多 4 位小数。 */
    private static BigDecimal parseRate(String s) {
        if (s == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = toDecimal(s, "税率");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("税率必须在 0~1 之间（如 0.13）");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("税率最多支持 4 位小数");
        }
        return value;
    }

    /** 非负金额：空按 0。 */
    private static BigDecimal parseNonNegative(String s, String label) {
        if (s == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = toDecimal(s, label);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + "不能小于 0");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + "最多支持 2 位小数");
        }
        return value;
    }

    private static BigDecimal toDecimal(String s, String label) {
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private static String unwrapMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getMessage() : t.getMessage();
    }

    private static class RawRow {
        final int rowIndex;
        final Map<Integer, String> row;

        RawRow(int rowIndex, Map<Integer, String> row) {
            this.rowIndex = rowIndex;
            this.row = row;
        }
    }

    private static class ParsedRow {
        int rowIndex;
        Long contractId;
        RevenueRecord record;
    }
}
