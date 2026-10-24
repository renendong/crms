package com.company.crms.payment;

import com.alibaba.excel.EasyExcel;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.CreateRecordDTO;
import com.company.crms.payment.service.PaymentRecordService;
import com.company.crms.payment.service.impl.PaymentImportServiceImpl;
import com.company.crms.common.response.ImportResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Excel 批量导入回款的集成 / 单测：
 * 构造 .xlsx 内存流，验证成功 + 失败行的统计与错误回执。
 */
@ExtendWith(MockitoExtension.class)
class PaymentImportTest {

    @Mock ContractMapper contractMapper;
    @Mock PaymentRecordService recordService;

    @InjectMocks PaymentImportServiceImpl importService;

    private byte[] buildExcel(List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(Arrays.asList(
                        java.util.Collections.singletonList("合同编号"),
                        java.util.Collections.singletonList("到账日期"),
                        java.util.Collections.singletonList("金额"),
                        java.util.Collections.singletonList("付款方"),
                        java.util.Collections.singletonList("凭证号"),
                        java.util.Collections.singletonList("备注")
                ))
                .sheet("回款")
                .doWrite(rows);
        return out.toByteArray();
    }

    @Test
    void should_import_success_rows_and_record_errors() {
        Contract c = new Contract();
        c.setId(1L);
        c.setCode("CT-A");
        when(contractMapper.selectOne(any())).thenReturn(c);
        when(recordService.create(any(CreateRecordDTO.class))).thenReturn(11L);

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "1000.00", "客户A", "V001", "备注1"),
                Arrays.asList("CT-A", "bad-date", "2000.00", "客户B", "V002", ""),
                Arrays.asList("CT-A", "2026-04-02", "abc",     "客户C", "V003", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);

        ImportResultVO r = importService.importRecords(file);

        assertEquals(3, r.getTotal());
        assertEquals(1, r.getSuccess());
        assertEquals(2, r.getFailed());
        assertEquals(2, r.getErrors().size());
        assertTrue(r.getErrors().stream().anyMatch(e -> e.getMessage().contains("到账日期")));
        assertTrue(r.getErrors().stream().anyMatch(e -> e.getMessage().contains("金额")));

        verify(recordService).create(any());
    }

    @Test
    void should_fail_when_contract_not_found() {
        when(contractMapper.selectOne(any())).thenReturn(null);

        byte[] data = buildExcel(List.of(
                Arrays.asList("UNKNOWN", "2026-04-01", "1000", "客户A", "V001", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getTotal());
        assertEquals(0, r.getSuccess());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("不存在"));
    }

    @Test
    void parser_supports_amount_with_thousands_separator() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);
        when(recordService.create(any(CreateRecordDTO.class))).thenAnswer(invocation -> {
            CreateRecordDTO dto = invocation.getArgument(0);
            assertEquals(new BigDecimal("1234567.89"), dto.getAmount());
            return 1L;
        });

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "1,234,567.89", "客户A", "V001", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getSuccess());
    }

    @Test
    void should_skip_empty_row() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);
        when(recordService.create(any(CreateRecordDTO.class))).thenReturn(1L);

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "1000", "客户A", "V001", ""),
                Arrays.asList("", "", "", "", "", ""),
                Arrays.asList("CT-A", "2026-04-02", "2000", "客户B", "V002", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(2, r.getTotal());
        assertEquals(2, r.getSuccess());
        verify(recordService, times(2)).create(any());
    }

    @Test
    void should_fail_when_amount_has_three_decimals() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "100.123", "客户A", "V001", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("2 位小数"));
    }

    @Test
    void should_parse_excel_serial_date() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);
        when(recordService.create(any(CreateRecordDTO.class))).thenAnswer(invocation -> {
            CreateRecordDTO dto = invocation.getArgument(0);
            assertEquals(java.time.LocalDate.of(2025, 1, 1), dto.getArrivalDate());
            return 1L;
        });

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "45658", "1000", "客户A", "V001", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getSuccess());
    }

    @Test
    void should_fail_second_duplicate_voucher_in_same_file() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);
        when(recordService.create(any(CreateRecordDTO.class))).thenReturn(1L);

        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "1000", "客户A", "V-DUP", ""),
                Arrays.asList("CT-A", "2026-04-02", "2000", "客户B", "V-DUP", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getSuccess());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("文件内已存在相同的凭证号"));
    }

    @Test
    void should_fail_when_payer_exceeds_max_length() {
        Contract c = new Contract();
        c.setId(1L);
        when(contractMapper.selectOne(any())).thenReturn(c);

        String longPayer = "x".repeat(101);
        byte[] data = buildExcel(List.of(
                Arrays.asList("CT-A", "2026-04-01", "1000", longPayer, "V001", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", data);

        ImportResultVO r = importService.importRecords(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("付款方"));
    }

    @Test
    void should_return_header_error_when_columns_wrong() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(Arrays.asList(
                        java.util.Collections.singletonList("合同号"),
                        java.util.Collections.singletonList("日期"),
                        java.util.Collections.singletonList("金额"),
                        java.util.Collections.singletonList("付款方"),
                        java.util.Collections.singletonList("凭证号"),
                        java.util.Collections.singletonList("备注")
                ))
                .sheet("回款")
                .doWrite(List.of(Arrays.asList("CT-A", "2026-04-01", "1000", "A", "V001", "")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "records.xlsx", "x", out.toByteArray());

        ImportResultVO r = importService.importRecords(file);
        assertEquals(0, r.getTotal());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("表头不正确"));
    }
}
