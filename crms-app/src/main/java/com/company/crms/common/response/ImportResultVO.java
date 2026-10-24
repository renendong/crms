package com.company.crms.common.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 批量导入结果：总条数、成功数、失败数与逐行错误。
 */
@Data
public class ImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int total;
    private int success;
    private int failed;
    private List<RowError> errors = new ArrayList<>();

    @Data
    public static class RowError {
        private int row;
        private String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
