package com.company.crms.common.util;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.EasyExcel;

/**
 * Excel 批量导入通用校验工具。
 *
 * <p>提供表头校验、Excel 序列号日期解析、金额精度校验、长度校验、空行判断等能力，
 * 供客户 / 合同 / 回款三个导入服务复用。
 */
public final class ExcelImportHelper {

    /** 单次导入最大行数，防止超大文件拖垮服务。 */
    public static final int MAX_ROWS = 2000;

    private ExcelImportHelper() {
    }

    /**
     * 生成导入模板：仅写入表头行。
     *
     * @param out       输出流（由 Controller 写入响应）
     * @param headers   表头列名
     * @param sheetName 工作表名
     */
    public static void writeTemplate(OutputStream out, String[] headers, String sheetName) {
        List<List<String>> head = java.util.Arrays.stream(headers)
                .map(java.util.Collections::singletonList)
                .toList();
        EasyExcel.write(out).head(head).sheet(sheetName).doWrite(java.util.Collections.emptyList());
    }

    /**
     * 校验表头列名是否与期望一致（trim 后逐列比较）。
     *
     * @param expected 期望列名（顺序敏感）
     * @param head     实际表头（key 为列索引）
     * @param module   模块名，用于错误提示
     * @throws IllegalArgumentException 列数或列名不一致时抛出
     */
    public static void validateHeader(String[] expected, Map<Integer, String> head, String module) {
        if (head == null || head.size() < expected.length) {
            throw new IllegalArgumentException(module + "导入模板列数不足，期望 " + expected.length + " 列");
        }
        StringBuilder mismatch = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            String actual = trim(head.get(i));
            if (!expected[i].equals(actual)) {
                mismatch.append("第").append(i + 1).append("列期望「").append(expected[i])
                        .append("」实际「").append(actual == null ? "" : actual).append("」; ");
            }
        }
        if (mismatch.length() > 0) {
            throw new IllegalArgumentException(module + "导入模板表头不正确：" + mismatch
                    + "请下载最新模板后再导入");
        }
    }

    /**
     * 解析日期：依次尝试 yyyy-MM-dd、yyyy/M/d、Excel 序列号（1900 日期系统）。
     *
     * @param s     单元格字符串
     * @param label 字段名，用于错误提示
     */
    public static LocalDate parseDateFlexible(String s, String label) {
        if (s == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String v = s.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        // 1) ISO 日期
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_DATE);
        } catch (Exception ignored) {
        }
        // 2) yyyy/M/d
        try {
            return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy/M/d"));
        } catch (Exception ignored) {
        }
        // 3) Excel 序列号（仅 1970-01-01 ~ 2034-12-31 区间，避免把普通数字误判为日期）
        try {
            long serial = Long.parseLong(v);
            if (serial >= 25569L && serial <= 60000L) {
                return LocalDate.of(1899, 12, 30).plusDays(serial);
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(label + "格式错误，请用 yyyy-MM-dd");
    }

    /**
     * 解析金额：必须 > 0，且符合 DECIMAL(18,2)（整数位 ≤16，小数位 ≤2）。
     */
    public static BigDecimal parseAmount(String s, String label) {
        if (s == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String v = s.trim().replace(",", "");
        if (v.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "格式错误");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        // 去掉末尾零后判断小数位数
        int scale = value.stripTrailingZeros().scale();
        if (scale > 2) {
            throw new IllegalArgumentException(label + "最多支持 2 位小数");
        }
        // 整数位 ≤ 16（DECIMAL(18,2)）
        if (value.precision() - Math.max(scale, 0) > 16) {
            throw new IllegalArgumentException(label + "整数位不能超过 16 位");
        }
        return value;
    }

    /**
     * 校验字符串长度上限。
     */
    public static void checkLength(String s, int max, String label) {
        if (s != null && s.length() > max) {
            throw new IllegalArgumentException(label + "不能超过 " + max + " 个字符");
        }
    }

    /**
     * 判断一行是否全空（所有单元格均为 null 或空白）。
     */
    public static boolean isEmptyRow(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row.values()) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
