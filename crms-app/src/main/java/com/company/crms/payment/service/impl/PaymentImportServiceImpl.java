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
import com.company.crms.payment.dto.CreateRecordDTO;
import com.company.crms.payment.service.PaymentImportService;
import com.company.crms.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentImportServiceImpl implements PaymentImportService {

    private static final String[] HEADERS = {
            "合同编号", "期数", "到账日期", "金额", "付款方", "凭证号", "备注"
    };

    private final ContractMapper contractMapper;
    private final PaymentRecordService recordService;

    @Override
    @OperationLog(module = "回款", action = "批量导入", type = "CREATE", recordParams = false)
    public ImportResultVO importRecords(MultipartFile file) {
        ImportResultVO result = new ImportResultVO();
        Set<String> seenVoucher = new HashSet<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {

                private boolean headerValid = false;
                private boolean headerChecked = false;

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    ExcelImportHelper.validateHeader(HEADERS, headMap, "回款");
                    headerValid = true;
                    headerChecked = true;
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                    if (!headerChecked) {
                        ExcelImportHelper.validateHeader(HEADERS, row, "回款");
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
                        importRow(row, seenVoucher);
                        result.setSuccess(result.getSuccess() + 1);
                    } catch (Exception ex) {
                        result.setFailed(result.getFailed() + 1);
                        result.getErrors().add(new ImportResultVO.RowError(rowIndex, ex.getMessage()));
                        log.warn("[import] row {} failed: {}", rowIndex, ex.getMessage());
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

    private void importRow(Map<Integer, String> row, Set<String> seenVoucher) {
        String contractCode = ExcelImportHelper.trim(row.get(0));
        String periodStr = ExcelImportHelper.trim(row.get(1));
        String arrivalStr = ExcelImportHelper.trim(row.get(2));
        String amountStr = ExcelImportHelper.trim(row.get(3));
        String payer = ExcelImportHelper.trim(row.get(4));
        String voucherNo = ExcelImportHelper.trim(row.get(5));
        String remark = ExcelImportHelper.trim(row.get(6));

        if (contractCode == null) {
            throw new IllegalArgumentException("合同编号不能为空");
        }
        Contract c = contractMapper.selectOne(new QueryWrapper<Contract>()
                .eq("code", contractCode)
                .eq("is_deleted", 0));
        if (c == null) {
            throw new IllegalArgumentException("合同 [" + contractCode + "] 不存在");
        }
        if (periodStr == null) {
            throw new IllegalArgumentException("期数不能为空");
        }
        int periodNo;
        try {
            periodNo = Integer.parseInt(periodStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("期数必须是整数");
        }
        if (periodNo <= 0) {
            throw new IllegalArgumentException("期数必须大于 0");
        }
        LocalDate arrival = ExcelImportHelper.parseDateFlexible(arrivalStr, "到账日期");
        BigDecimal amount = ExcelImportHelper.parseAmount(amountStr, "金额");
        ExcelImportHelper.checkLength(payer, 100, "付款方");
        if (voucherNo != null) {
            ExcelImportHelper.checkLength(voucherNo, 255, "凭证号");
            if (!seenVoucher.add(voucherNo)) {
                throw new IllegalArgumentException("文件内已存在相同的凭证号：" + voucherNo);
            }
        }
        ExcelImportHelper.checkLength(remark, 500, "备注");

        CreateRecordDTO dto = new CreateRecordDTO();
        dto.setContractId(c.getId());
        dto.setPeriodNo(periodNo);
        dto.setArrivalDate(arrival);
        dto.setAmount(amount);
        dto.setPayer(payer);
        dto.setVoucherNo(voucherNo);
        dto.setRemark(remark);
        recordService.create(dto);
    }
}
