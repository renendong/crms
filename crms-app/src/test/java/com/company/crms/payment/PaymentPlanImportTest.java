package com.company.crms.payment;

import com.alibaba.excel.EasyExcel;
import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.payment.service.impl.PaymentPlanImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回款计划 Excel 批量导入单测：按合同整组解析、整组写入与整组失败回写。
 */
@ExtendWith(MockitoExtension.class)
class PaymentPlanImportTest {

    @Mock ContractMapper contractMapper;
    @Mock PaymentPlanService planService;

    @InjectMocks PaymentPlanImportServiceImpl importService;

    private static final List<String> HEADER = List.of(
            "合同编号", "期数", "计划日期", "计划金额", "提醒天数", "备注");

    private byte[] buildExcel(List<String> header, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(header.stream().map(java.util.Collections::singletonList).toList())
                .sheet("回款计划")
                .doWrite(rows);
        return out.toByteArray();
    }

    private MockMultipartFile file(byte[] data) {
        return new MockMultipartFile(
                "file", "plans.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);
    }

    private Contract contract(long id) {
        Contract c = new Contract();
        c.setId(id);
        c.setCode("CT-A");
        c.setAmount(new java.math.BigDecimal("10000.00"));
        return c;
    }

    @Test
    void should_save_one_group_when_total_equals_contract_amount() {
        when(contractMapper.selectOne(any())).thenReturn(contract(1L));
        when(planService.batchSave(any())).thenReturn(List.of(1L, 2L));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("CT-A", "1", "2026-01-01", "4000.00", "7", ""),
                Arrays.asList("CT-A", "2", "2026-02-01", "6000.00", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(2, r.getTotal());
        assertEquals(2, r.getSuccess());
        assertEquals(0, r.getFailed());

        ArgumentCaptor<BatchPlanDTO> captor = ArgumentCaptor.forClass(BatchPlanDTO.class);
        verify(planService).batchSave(captor.capture());
        assertEquals(1L, captor.getValue().getContractId());
        assertEquals(2, captor.getValue().getPlans().size());
        assertEquals(Boolean.TRUE, captor.getValue().getOverwrite());
    }

    @Test
    void group_failure_should_mark_every_row_of_that_contract() {
        when(contractMapper.selectOne(any())).thenReturn(contract(1L));
        when(planService.batchSave(any())).thenThrow(
                new BizException(ErrorCode.PM_PLAN_AMOUNT_MISMATCH,
                        "回款计划合计 9000.00 与合同金额 10000.00 不一致，差额 1000.00"));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("CT-A", "1", "2026-01-01", "4000.00", "7", ""),
                Arrays.asList("CT-A", "2", "2026-02-01", "5000.00", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(2, r.getTotal());
        assertEquals(0, r.getSuccess());
        assertEquals(2, r.getFailed());
        assertEquals(2, r.getErrors().size());
        assertTrue(r.getErrors().stream().allMatch(e -> e.getMessage().contains("差额 1000.00")));
    }

    @Test
    void group_failure_should_report_duplicate_period_for_every_row() {
        when(contractMapper.selectOne(any())).thenReturn(contract(1L));
        when(planService.batchSave(any())).thenThrow(
                new BizException(ErrorCode.PM_PLAN_PERIOD_DUPLICATE, "第 1 期重复"));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("CT-A", "1", "2026-01-01", "4000.00", "7", ""),
                Arrays.asList("CT-A", "1", "2026-02-01", "6000.00", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(2, r.getFailed());
        assertTrue(r.getErrors().stream().allMatch(e -> e.getMessage().contains("第 1 期重复")));
    }

    @Test
    void should_fail_row_when_contract_not_found() {
        when(contractMapper.selectOne(any())).thenReturn(null);

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("UNKNOWN", "1", "2026-01-01", "4000.00", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("不存在"));
    }

    @Test
    void should_fail_row_when_period_missing() {
        when(contractMapper.selectOne(any())).thenReturn(contract(1L));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("CT-A", "", "2026-01-01", "4000.00", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("期数不能为空"));
    }

    @Test
    void should_fail_row_when_plan_amount_has_three_decimals() {
        when(contractMapper.selectOne(any())).thenReturn(contract(1L));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("CT-A", "1", "2026-01-01", "4000.123", "7", "")
        ));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("2 位小数"));
    }

    @Test
    void should_return_header_error_when_columns_wrong() {
        byte[] data = buildExcel(
                List.of("合同号", "期数", "计划日期", "金额", "提醒天数", "备注"),
                List.of(Arrays.asList("CT-A", "1", "2026-01-01", "4000.00", "7", "")));

        ImportResultVO r = importService.importPlans(file(data));

        assertEquals(0, r.getTotal());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("表头不正确"));
    }

    @Test
    void should_reject_more_than_max_rows() {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 2001; i++) {
            rows.add(Arrays.asList("CT-A", String.valueOf(i), "2026-01-01", "1.00", "7", ""));
        }

        byte[] data = buildExcel(HEADER, rows);

        assertThrows(RuntimeException.class, () -> importService.importPlans(file(data)));
        verify(planService, times(0)).batchSave(any());
    }
}
