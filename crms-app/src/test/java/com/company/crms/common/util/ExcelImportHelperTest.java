package com.company.crms.common.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExcelImportHelper} 单元测试：覆盖日期/金额解析、表头校验、长度校验、空行判断。
 */
class ExcelImportHelperTest {

    // ---------------- parseDateFlexible ----------------

    @Test
    void parseDate_supports_iso_date() {
        assertEquals(LocalDate.of(2026, 4, 1),
                ExcelImportHelper.parseDateFlexible("2026-04-01", "签订日期"));
    }

    @Test
    void parseDate_supports_slash_date() {
        assertEquals(LocalDate.of(2026, 4, 1),
                ExcelImportHelper.parseDateFlexible("2026/4/1", "签订日期"));
    }

    @Test
    void parseDate_supports_excel_serial() {
        // 45658 => 2025-01-01（1900 日期系统）
        assertEquals(LocalDate.of(2025, 1, 1),
                ExcelImportHelper.parseDateFlexible("45658", "签订日期"));
    }

    @Test
    void parseDate_rejects_null() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseDateFlexible(null, "签订日期"));
    }

    @Test
    void parseDate_rejects_invalid() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseDateFlexible("not-a-date", "签订日期"));
    }

    // ---------------- parseAmount ----------------

    @Test
    void parseAmount_normal() {
        assertEquals(new BigDecimal("100.00"),
                ExcelImportHelper.parseAmount("100.00", "金额"));
    }

    @Test
    void parseAmount_supports_thousands_separator() {
        assertEquals(new BigDecimal("1234567.89"),
                ExcelImportHelper.parseAmount("1,234,567.89", "金额"));
    }

    @Test
    void parseAmount_rejects_three_decimals() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseAmount("100.123", "金额"));
    }

    @Test
    void parseAmount_rejects_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseAmount("0", "金额"));
    }

    @Test
    void parseAmount_rejects_negative() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseAmount("-1", "金额"));
    }

    @Test
    void parseAmount_rejects_non_numeric() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseAmount("abc", "金额"));
    }

    @Test
    void parseAmount_rejects_too_large_integer() {
        // 17 位整数 => DECIMAL(18,2) 不允许
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.parseAmount("12345678901234567", "金额"));
    }

    // ---------------- validateHeader ----------------

    @Test
    void validateHeader_pass_when_exact_match() {
        String[] expected = {"列A", "列B"};
        Map<Integer, String> head = new HashMap<>();
        head.put(0, "列A");
        head.put(1, "列B");
        ExcelImportHelper.validateHeader(expected, head, "测试");
    }

    @Test
    void validateHeader_fail_when_column_name_mismatch() {
        String[] expected = {"列A", "列B"};
        Map<Integer, String> head = new HashMap<>();
        head.put(0, "列A");
        head.put(1, "列C");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.validateHeader(expected, head, "测试"));
        assertTrue(ex.getMessage().contains("列B"));
        assertTrue(ex.getMessage().contains("列C"));
    }

    @Test
    void validateHeader_fail_when_too_few_columns() {
        String[] expected = {"列A", "列B", "列C"};
        Map<Integer, String> head = new HashMap<>();
        head.put(0, "列A");
        head.put(1, "列B");
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.validateHeader(expected, head, "测试"));
    }

    // ---------------- checkLength ----------------

    @Test
    void checkLength_pass_within_limit() {
        ExcelImportHelper.checkLength("abc", 5, "名称");
    }

    @Test
    void checkLength_fail_exceeds_limit() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelImportHelper.checkLength("abcdef", 5, "名称"));
    }

    @Test
    void checkLength_pass_when_null() {
        ExcelImportHelper.checkLength(null, 5, "名称");
    }

    // ---------------- isEmptyRow ----------------

    @Test
    void isEmptyRow_true_when_all_blank() {
        Map<Integer, String> row = new HashMap<>();
        row.put(0, "");
        row.put(1, "  ");
        row.put(2, null);
        assertTrue(ExcelImportHelper.isEmptyRow(row));
    }

    @Test
    void isEmptyRow_false_when_has_value() {
        Map<Integer, String> row = new HashMap<>();
        row.put(0, "");
        row.put(1, "x");
        assertFalse(ExcelImportHelper.isEmptyRow(row));
    }

    @Test
    void isEmptyRow_true_when_empty_map() {
        assertTrue(ExcelImportHelper.isEmptyRow(new HashMap<>()));
    }

    @Test
    void isEmptyRow_true_when_null() {
        assertTrue(ExcelImportHelper.isEmptyRow(null));
    }

    // ---------------- trim ----------------

    @Test
    void trim_returns_null_for_null_or_blank() {
        assertEquals(null, ExcelImportHelper.trim(null));
        assertEquals(null, ExcelImportHelper.trim(""));
        assertEquals(null, ExcelImportHelper.trim("   "));
    }

    @Test
    void trim_strips_surrounding_spaces() {
        assertEquals("abc", ExcelImportHelper.trim("  abc  "));
    }

    // ---------------- writeTemplate ----------------

    @Test
    void writeTemplate_writes_expected_header() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] headers = {"列A", "列B", "列C"};
        ExcelImportHelper.writeTemplate(out, headers, "模板页");

        List<Map<Integer, String>> heads = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(out.toByteArray()),
                new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                        heads.add(headMap);
                    }

                    @Override
                    public void invoke(Map<Integer, String> data, AnalysisContext context) {
                        /* 模板不含数据行，不应被调用 */
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        /* nothing */
                    }
                }).headRowNumber(1).sheet().doRead();

        assertEquals(1, heads.size());
        assertEquals("列A", heads.get(0).get(0));
        assertEquals("列B", heads.get(0).get(1));
        assertEquals("列C", heads.get(0).get(2));
    }
}
