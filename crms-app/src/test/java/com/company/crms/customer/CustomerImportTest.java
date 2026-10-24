package com.company.crms.customer;

import com.alibaba.excel.EasyExcel;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.customer.dto.CreateCustomerDTO;
import com.company.crms.customer.service.CustomerService;
import com.company.crms.customer.service.impl.CustomerImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户档案 Excel 批量导入单测：构造内存 .xlsx 验证成功行、失败行与校验回执。
 */
@ExtendWith(MockitoExtension.class)
class CustomerImportTest {

    @Mock CustomerService customerService;

    @InjectMocks CustomerImportServiceImpl importService;

    private byte[] buildExcel(List<String> header, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
                .head(header.stream().map(java.util.Collections::singletonList).toList())
                .sheet("客户")
                .doWrite(rows);
        return out.toByteArray();
    }

    private static final List<String> HEADER = List.of(
            "客户名称", "客户类型", "统一社会信用代码", "客户等级",
            "所属行业", "地区编码", "地址", "备注");

    @Test
    void should_import_success_rows_and_skip_empty_row() {
        when(customerService.create(any(CreateCustomerDTO.class))).thenReturn(1L);

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("客户甲", "企业", "91310000MA1FL1XX2A", "A", "软件", "310000", "上海", "备注1"),
                Arrays.asList("", "", "", "", "", "", "", ""),   // 空行应跳过
                Arrays.asList("客户乙", "个人", "", "C", "咨询", "310000", "", "备注2")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);

        ImportResultVO r = importService.importCustomers(file);

        assertEquals(2, r.getTotal());  // 空行不计入
        assertEquals(2, r.getSuccess());
        assertEquals(0, r.getFailed());
        verify(customerService, times(2)).create(any());
    }

    @Test
    void should_fail_row_when_name_missing() {
        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("", "企业", "", "A", "", "", "", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "x", data);

        ImportResultVO r = importService.importCustomers(file);
        assertEquals(1, r.getTotal());
        assertEquals(0, r.getSuccess());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("客户名称"));
    }

    @Test
    void should_fail_row_when_uscc_format_invalid() {
        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("客户甲", "企业", "BAD-USCC", "A", "", "", "", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "x", data);

        ImportResultVO r = importService.importCustomers(file);
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("统一社会信用代码"));
    }

    @Test
    void should_fail_second_duplicate_uscc_in_same_file() {
        when(customerService.create(any(CreateCustomerDTO.class))).thenReturn(1L);

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("客户甲", "企业", "91310000MA1FL1XX2A", "A", "", "", "", ""),
                Arrays.asList("客户乙", "企业", "91310000MA1FL1XX2A", "B", "", "", "", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "x", data);

        ImportResultVO r = importService.importCustomers(file);
        assertEquals(1, r.getSuccess());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("文件内已存在相同的统一社会信用代码"));
    }

    @Test
    void should_return_header_error_when_column_name_wrong() {
        byte[] data = buildExcel(
                List.of("客户名", "类型", "信用代码", "等级", "行业", "地区", "地址", "备注"),
                List.of(Arrays.asList("客户甲", "企业", "", "A", "", "", "", ""))
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "x", data);

        ImportResultVO r = importService.importCustomers(file);
        assertEquals(0, r.getTotal());
        assertEquals(1, r.getFailed());
        assertTrue(r.getErrors().get(0).getMessage().contains("表头不正确"));
    }

    @Test
    void should_default_type_and_level_when_blank() {
        when(customerService.create(any(CreateCustomerDTO.class))).thenAnswer(invocation -> {
            CreateCustomerDTO dto = invocation.getArgument(0);
            assertEquals("ENTERPRISE", dto.getType());
            assertEquals("C", dto.getLevel());
            return 1L;
        });

        byte[] data = buildExcel(HEADER, List.of(
                Arrays.asList("客户甲", "", "", "", "", "", "", "")
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "x", data);

        ImportResultVO r = importService.importCustomers(file);
        assertEquals(1, r.getSuccess());
    }
}
