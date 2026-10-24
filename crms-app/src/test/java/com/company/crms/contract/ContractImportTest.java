package com.company.crms.contract;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.contract.dto.CreateContractDTO;
import com.company.crms.contract.service.ContractService;
import com.company.crms.contract.service.impl.ContractImportServiceImpl;
import com.company.crms.customer.entity.Customer;
import com.company.crms.customer.mapper.CustomerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合同 Excel 批量导入单测：构造内存 .xlsx 验证成功行、失败行与校验回执。
 */
@ExtendWith(MockitoExtension.class)
class ContractImportTest {

    @Mock ContractService contractService;
    @Mock CustomerMapper customerMapper;

    @InjectMocks ContractImportServiceImpl importService;

    private byte[] buildExcel(List<String> header, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(header.stream().map(java.util.Collections::singletonList).toList())
                .sheet("合同")
                .doWrite(rows);
        return out.toByteArray();
    }

    private static final List<String> HEADER = List.of(
            "合同名称", "合同类型", "客户名称", "合同金额",
            "签订日期", "履约开始日期", "履约结束日期", "提醒天数", "备注");

    private void mockCustomer(String name) {
        Customer c = new Customer();
        c.setId(100L);
        c.setName(name);
        when(customerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(c));
    }

    @Test
    void should_import_success_row_and_skip_empty_row() {
        mockCustomer("客户甲");
        when(contractService.create(any(CreateContractDTO.class))).thenReturn(1L);

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "客户甲", "50000", "2026-01-01", "2026-01-01", "2026-12-31", "30", "备注"),
                Arrays.asList("", "", "", "", "", "", "", "", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getTotal());
        assertEquals(1, r.getSuccess());
        assertEquals(0, r.getFailed());
        verify(contractService, times(1)).create(any());
    }

    @Test
    void should_fail_when_amount_has_three_decimals() {
        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "客户甲", "100.123", "2026-01-01", "2026-01-01", "2026-12-31", "30", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("2 位小数"));
    }

    @Test
    void should_parse_excel_serial_date_and_succeed() {
        mockCustomer("客户甲");
        when(contractService.create(any(CreateContractDTO.class))).thenAnswer(invocation -> {
            CreateContractDTO dto = invocation.getArgument(0);
            assertEquals(LocalDate.of(2025, 1, 1), dto.getSignedAt());
            assertEquals(new BigDecimal("80000"), dto.getAmount());
            return 1L;
        });

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "客户甲", "80000", "45658", "45658", "46000", "30", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getSuccess());
    }

    @Test
    void should_fail_when_customer_not_found() {
        when(customerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "不存在", "50000", "2026-01-01", "2026-01-01", "2026-12-31", "30", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("不存在"));
    }

    @Test
    void should_fail_when_end_before_start() {
        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "客户甲", "50000", "2026-01-01", "2026-12-31", "2026-01-01", "30", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("履约结束日期"));
    }

    @Test
    void should_return_header_error_when_columns_wrong() {
        byte[] data = buildExcel(
                List.of("合同名", "类型", "客户", "金额", "签订", "开始", "结束", "提醒", "备注"),
                List.of(Arrays.asList("合同甲", "销售", "客户甲", "50000", "2026-01-01", "2026-01-01", "2026-12-31", "30", ""))
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(0, r.getTotal());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("表头不正确"));
    }

    @Test
    void should_fail_when_customer_name_ambiguous() {
        Customer c1 = new Customer(); c1.setId(1L); c1.setName("客户甲");
        Customer c2 = new Customer(); c2.setId(2L); c2.setName("客户甲");
        when(customerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(c1, c2));

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("合同甲", "销售", "客户甲", "50000", "2026-01-01", "2026-01-01", "2026-12-31", "30", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "contracts.xlsx", "x", data);

        ImportResultVO r = importService.importContracts(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("多条记录"));
    }
}
