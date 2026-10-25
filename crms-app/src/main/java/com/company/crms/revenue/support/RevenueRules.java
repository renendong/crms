package com.company.crms.revenue.support;

import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.revenue.entity.RevenueRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

/**
 * 收入确认的字段级规则：金额派生、期间校验、收入类型归一化。
 */
public final class RevenueRules {

    public static final String TYPE_GOODS = "GOODS";
    public static final String TYPE_SERVICE = "SERVICE";
    public static final String TYPE_OTHER = "OTHER";

    private static final Set<String> TYPES = Set.of(TYPE_GOODS, TYPE_SERVICE, TYPE_OTHER);

    private RevenueRules() {
    }

    /**
     * 收入类型归一化：兼容中文「商品/货物、服务、其他」与英文枚举，空默认 SERVICE。
     */
    public static String normalizeIncomeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return TYPE_SERVICE;
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "GOODS", "商品", "货物", "产品" -> TYPE_GOODS;
            case "SERVICE", "服务", "劳务" -> TYPE_SERVICE;
            case "OTHER", "其他", "其它" -> TYPE_OTHER;
            default -> throw new IllegalArgumentException("收入类型只能是 商品/服务/其他 或 GOODS/SERVICE/OTHER");
        };
    }

    public static boolean isValidType(String type) {
        return type != null && TYPES.contains(type);
    }

    /**
     * 校验并归一化归属期间：YYYY-MM，且不得晚于当前月。
     */
    public static String checkPeriodMonth(String periodMonth) {
        if (periodMonth == null || periodMonth.isBlank()) {
            throw new BizException(ErrorCode.RV_PERIOD_INVALID, "收入归属期间不能为空");
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(periodMonth.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.RV_PERIOD_INVALID, "收入归属期间格式应为 YYYY-MM");
        }
        if (ym.isAfter(YearMonth.now())) {
            throw new BizException(ErrorCode.RV_PERIOD_INVALID,
                    "收入归属期间 " + periodMonth + " 不能晚于当前月");
        }
        return ym.toString();
    }

    /**
     * 派生税额 / 不含税金额 / 毛利。
     *
     * <p>amount 为含税金额（>0，≤2 位小数）；taxRate ∈ [0,1] 且 ≤4 位小数；
     * costAmount ≥ 0 且 ≤2 位小数，空按 0 处理。
     */
    public static void applyDerived(RevenueRecord r) {
        BigDecimal amount = r.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "含税金额必须大于 0");
        }
        if (scaleOf(amount) > 2) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "含税金额最多支持 2 位小数");
        }
        BigDecimal taxRate = r.getTaxRate() == null ? BigDecimal.ZERO : r.getTaxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "税率必须在 0~1 之间");
        }
        if (scaleOf(taxRate) > 4) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "税率最多支持 4 位小数");
        }
        BigDecimal cost = r.getCostAmount() == null ? BigDecimal.ZERO : r.getCostAmount();
        if (cost.signum() < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "成本金额不能小于 0");
        }
        if (scaleOf(cost) > 2) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "成本金额最多支持 2 位小数");
        }

        BigDecimal taxAmount = amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = amount.subtract(taxAmount);
        BigDecimal grossProfit = netAmount.subtract(cost);

        r.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        r.setTaxRate(taxRate);
        r.setTaxAmount(taxAmount);
        r.setNetAmount(netAmount);
        r.setCostAmount(cost.setScale(2, RoundingMode.HALF_UP));
        r.setGrossProfit(grossProfit);
        if (r.getIncomeType() == null) {
            r.setIncomeType(TYPE_SERVICE);
        }
    }

    /** 确认日期不得为空，且不得晚于今天。 */
    public static void checkRecognizeDate(LocalDate date) {
        if (date == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "收入确认日期不能为空");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "收入确认日期不能晚于今天");
        }
    }

    private static int scaleOf(BigDecimal v) {
        return v.stripTrailingZeros().scale();
    }

    /**
     * 由请求 DTO 构造收入实体并完成金额派生。
     */
    public static RevenueRecord fromDTO(com.company.crms.revenue.dto.CreateRevenueDTO dto) {
        if (dto.getContractId() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "合同不能为空");
        }
        checkRecognizeDate(dto.getRecognizeDate());
        RevenueRecord r = new RevenueRecord();
        r.setId(com.company.crms.common.util.SnowflakeIdGenerator.next());
        r.setContractId(dto.getContractId());
        r.setRecognizeDate(dto.getRecognizeDate());
        r.setIncomeType(normalizeIncomeType(dto.getIncomeType()));
        r.setAmount(dto.getAmount());
        r.setTaxRate(dto.getTaxRate());
        r.setCostAmount(dto.getCostAmount());
        r.setVoucherNo(dto.getVoucherNo());
        r.setRemark(dto.getRemark());
        r.setPeriodMonth(checkPeriodMonth(dto.getPeriodMonth()));
        applyDerived(r);
        return r;
    }
}
