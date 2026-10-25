package com.company.crms.revenue;

import com.alibaba.excel.EasyExcel;
import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import com.company.crms.revenue.service.impl.RevenueImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 收入明细 Excel 批量导入单测：行级字段校验 + 按合同整组时点平衡 / 合计校验。
 */
@ExtendWith(MockitoExtension.class)
class RevenueImportTest {

    @Mock ContractMapper contractMapper;
    @Mock RevenueRecordMapper revenueMapper;
    @Mock RevenueBalanceValidator balanceValidator;
    @Mock TransactionTemplate transactionTemplate;

    @InjectMocks RevenueImportServiceImpl importService;

    private static final List<String> HEADER = List.of(
            "合同编号", "收入归属期间", "确认日期", "收入类型",
            "含税金额", "税率", "成本金额", "单据号", "备注");

    private byte[] buildExcel(List<String> header, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(header.stream().map(java.util.Collections::singletonList).toList())
                .sheet("收入")
                .doWrite(rows);
        return out.toByteArray();
    }

    private MockMultipartFile file(byte[] data) {
        return new MockMultipartFile(
                "file", "revenues.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);
    }

    private List<Object> row(String contract, String period, String date, String type,
                             String amount, String rate, String cost, String voucher) {
        return Arrays.asList(contract, period, date, type, amount, rate, cost, voucher, "");
    }

    private Contract contract() {
        Contract c = new Contract();
        c.setId(1L);
        c.setCode("CT-A");
        c.setAmount(new java.math.BigDecimal("10000.00"));
        return c;
    }

    /** 让 Tile 事务模板真正执行回调，以便断言 insert 落库。 */
    private void stubTransaction() {
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void should_import_group_when_validation_passes() {
        when(contractMapper.selectOne(any())).thenReturn(contract());
        stubTransaction();

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "100.00", "V001"),
                row("CT-A", "2026-05", "2026-05-01", "商品", "6000.00", "0.13", "0", "V002")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(2, r.getTotal());
        assertEquals(2, r.getSuccess());
        assertEquals(0, r.getFailed());
        verify(balanceValidator).validateContract(eq(1L), anyList(), isNull());
        verify(revenueMapper, times(2)).insert(any(RevenueRecord.class));
    }

    @Test
    void should_fail_row_when_period_month_invalid() {
        when(contractMapper.selectOne(any())).thenReturn(contract());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026/04", "2026-04-01", "服务", "4000.00", "0.13", "0", "V001")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("YYYY-MM"));
        verifyNoInteractions(balanceValidator);
    }

    @Test
    void should_fail_row_when_tax_rate_out_of_range() {
        when(contractMapper.selectOne(any())).thenReturn(contract());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "1.5", "0", "V001")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("税率必须在 0~1 之间"));
    }

    @Test
    void should_fail_row_when_amount_has_three_decimals() {
        when(contractMapper.selectOne(any())).thenReturn(contract());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.123", "0.13", "0", "V001")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("2 位小数"));
    }

    @Test
    void should_fail_row_when_cost_has_three_decimals() {
        when(contractMapper.selectOne(any())).thenReturn(contract());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "1.001", "V001")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("成本金额最多支持 2 位小数"));
    }

    @Test
    void should_fail_second_duplicate_voucher_in_same_file() {
        when(contractMapper.selectOne(any())).thenReturn(contract());
        stubTransaction();

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "0", "V-DUP"),
                row("CT-A", "2026-04", "2026-04-02", "服务", "4000.00", "0.13", "0", "V-DUP")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(1, r.getSuccess());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("文件内已存在相同的单据号"));
    }

    @Test
    void should_fail_whole_group_when_contract_has_no_plan() {
        when(contractMapper.selectOne(any())).thenReturn(contract());
        doThrow(new BizException(ErrorCode.RV_PLAN_REQUIRED))
                .when(balanceValidator).validateContract(eq(1L), anyList(), isNull());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "0", "V001"),
                row("CT-A", "2026-05", "2026-05-01", "服务", "6000.00", "0.13", "0", "V002")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(2, r.getFailed());
        assertTrue(r.getErrors().stream()
                .allMatch(e -> e.getMessage().contains("该合同尚未录入有效回款计划")));
        verifyNoInteractions(revenueMapper);
        verify(transactionTemplate, times(0)).executeWithoutResult(any());
    }

    @Test
    void should_fail_whole_group_when_income_total_mismatch() {
        when(contractMapper.selectOne(any())).thenReturn(contract());
        doThrow(new BizException(ErrorCode.RV_AMOUNT_MISMATCH,
                "同一合同含税收入合计 9000.00 与合同金额 10000.00 不一致，差额 1000.00"))
                .when(balanceValidator).validateContract(eq(1L), anyList(), isNull());

        byte[] data = buildExcel(HEADER, List.of(
                row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "0", "V001"),
                row("CT-A", "2026-05", "2026-05-01", "服务", "5000.00", "0.13", "0", "V002")
        ));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(2, r.getFailed());
        assertTrue(r.getErrors().stream()
                .allMatch(e -> e.getMessage().contains("同一合同含税收入合计 9000.00")));
        verifyNoInteractions(revenueMapper);
    }

    @Test
    void should_return_header_error_when_columns_wrong() {
        byte[] data = buildExcel(
                List.of("合同号", "期间", "确认日期", "类型", "金额", "税率", "成本", "单据号", "备注"),
                List.of(row("CT-A", "2026-04", "2026-04-01", "服务", "4000.00", "0.13", "0", "V001")));

        ImportResultVO r = importService.importRevenues(file(data));

        assertEquals(0, r.getTotal());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("表头不正确"));
    }
}
