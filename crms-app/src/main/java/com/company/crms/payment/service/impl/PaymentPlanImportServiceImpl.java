package com.company.crms.payment.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.service.PaymentPlanImportService;
import com.company.crms.payment.service.PaymentPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanImportServiceImpl implements PaymentPlanImportService {

    private static final String[] HEADERS = {
            "合同编号", "期数", "计划日期", "计划金额", "提醒天数", "备注"
    };

    private final ContractMapper contractMapper;
    private final PaymentPlanService planService;

    @Override
    @OperationLog(module = "回款", action = "批量导入计划", type = "CREATE", recordParams = false)
    public ImportResultVO importPlans(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        List<RawRow> raws = readRows(file, result);

        // 1) 逐行解析，按合同分组
        Map<Long, List<ParsedRow>> groups = new LinkedHashMap<>();
        for (RawRow raw : raws) {
            result.setTotal(result.getTotal() + 1);
            try {
                ParsedRow parsed = parseRow(raw);
                groups.computeIfAbsent(parsed.contractId, k -> new ArrayList<>()).add(parsed);
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(new ImportResultVO.RowError(raw.rowIndex, ex.getMessage()));
                log.warn("[plan-import] row {} failed: {}", raw.rowIndex, ex.getMessage());
            }
        }

        // 2) 逐合同整组写入（合计必须等于合同金额）
        for (Map.Entry<Long, List<ParsedRow>> entry : groups.entrySet()) {
            List<ParsedRow> rows = entry.getValue();
            BatchPlanDTO dto = new BatchPlanDTO();
            dto.setContractId(entry.getKey());
            dto.setOverwrite(Boolean.TRUE);
            dto.setPlans(rows.stream().map(r -> r.item).toList());
            try {
                planService.batchSave(dto);
                result.setSuccess(result.getSuccess() + rows.size());
            } catch (Exception ex) {
                String msg = ex.getMessage();
                for (ParsedRow row : rows) {
                    result.setFailed(result.getFailed() + 1);
                    result.getErrors().add(new ImportResultVO.RowError(row.rowIndex, msg));
                }
                log.warn("[plan-import] contract {} group failed: {}", entry.getKey(), msg);
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
                    ExcelImportHelper.validateHeader(HEADERS, headMap, "回款计划");
                    headerChecked = true;
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                    if (!headerChecked) {
                        ExcelImportHelper.validateHeader(HEADERS, row, "回款计划");
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

    private ParsedRow parseRow(RawRow raw) {
        Map<Integer, String> row = raw.row;
        String contractCode = ExcelImportHelper.trim(row.get(0));
        String periodStr = ExcelImportHelper.trim(row.get(1));
        String planDateStr = ExcelImportHelper.trim(row.get(2));
        String planAmountStr = ExcelImportHelper.trim(row.get(3));
        String remindDaysStr = ExcelImportHelper.trim(row.get(4));
        String remark = ExcelImportHelper.trim(row.get(5));

        if (contractCode == null) {
            throw new IllegalArgumentException("合同编号不能为空");
        }
        Contract c = contractMapper.selectOne(new QueryWrapper<Contract>()
                .eq("code", contractCode)
                .eq("is_deleted", 0));
        if (c == null) {
            throw new IllegalArgumentException("合同 [" + contractCode + "] 不存在");
        }

        Integer periodNo = parseInt(periodStr, "期数", 1);
        LocalDate planDate = ExcelImportHelper.parseDateFlexible(planDateStr, "计划日期");
        java.math.BigDecimal planAmount = ExcelImportHelper.parseAmount(planAmountStr, "计划金额");
        Integer remindDays = null;
        if (remindDaysStr != null) {
            remindDays = parseInt(remindDaysStr, "提醒天数", 0);
        }
        ExcelImportHelper.checkLength(remark, 500, "备注");

        BatchPlanDTO.BatchPlanItem item = new BatchPlanDTO.BatchPlanItem();
        item.setPeriodNo(periodNo);
        item.setPlanDate(planDate);
        item.setPlanAmount(planAmount);
        item.setRemindDays(remindDays);
        item.setRemark(remark);

        ParsedRow parsed = new ParsedRow();
        parsed.rowIndex = raw.rowIndex;
        parsed.contractId = c.getId();
        parsed.item = item;
        return parsed;
    }

    private static Integer parseInt(String s, String label, int min) {
        if (s == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        int value;
        try {
            value = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
        if (value < min) {
            throw new IllegalArgumentException(label + "不能小于 " + min);
        }
        return value;
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
        BatchPlanDTO.BatchPlanItem item;
    }
}
