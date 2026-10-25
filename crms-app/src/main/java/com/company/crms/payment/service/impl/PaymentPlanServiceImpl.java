package com.company.crms.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.common.response.PageResult;
import com.company.crms.common.util.SnowflakeIdGenerator;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.dto.CreatePlanDTO;
import com.company.crms.payment.dto.GeneratePlansDTO;
import com.company.crms.payment.dto.PlanQuery;
import com.company.crms.payment.dto.UpdatePlanDTO;
import com.company.crms.payment.entity.PaymentPlan;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.payment.vo.PaymentPlanSummaryVO;
import com.company.crms.payment.vo.PaymentPlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private final PaymentPlanMapper planMapper;
    private final ContractMapper contractMapper;

    @Override
    public PageResult<PaymentPlanVO> page(PlanQuery query) {
        Page<PaymentPlan> page = Page.of(query.getPage(), query.getSize());
        QueryWrapper<PaymentPlan> w = new QueryWrapper<>();
        if (query.getContractId() != null) w.eq("contract_id", query.getContractId());
        if (query.getStatus() != null) w.eq("status", query.getStatus());
        if (Boolean.TRUE.equals(query.getOverdueOnly())) w.eq("is_overdue", 1);
        if (query.getFromDate() != null) w.ge("plan_date", query.getFromDate());
        if (query.getToDate() != null) w.le("plan_date", query.getToDate());
        w.orderByAsc("plan_date").orderByAsc("period_no");

        Page<PaymentPlan> result = (Page<PaymentPlan>) planMapper.selectPagePlans(page, w);
        List<PaymentPlanVO> vos = toVOs(result.getRecords());
        return PageResult.of(result, vos);
    }

    @Override
    public List<PaymentPlanVO> listByContract(Long contractId) {
        QueryWrapper<PaymentPlan> w = new QueryWrapper<PaymentPlan>()
                .eq("contract_id", contractId)
                .orderByAsc("period_no");
        return toVOs(planMapper.selectList(w));
    }

    @Override
    public PaymentPlanVO detail(Long id) {
        PaymentPlan p = planMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PM_PLAN_NOT_FOUND);
        }
        return toVOs(List.of(p)).get(0);
    }

    @Override
    @OperationLog(module = "回款", action = "新增计划", type = "CREATE")
    @Transactional
    public Long create(CreatePlanDTO dto) {
        Contract c = requireContract(dto.getContractId());
        PaymentPlan dup = planMapper.selectByContractAndPeriod(dto.getContractId(), dto.getPeriodNo());
        if (dup != null) {
            throw new BizException(ErrorCode.PM_PLAN_PERIOD_DUPLICATE,
                    "第 " + dto.getPeriodNo() + " 期已存在，请直接编辑");
        }
        assertTotalEqualsContract(c, null, dto.getPlanAmount());
        PaymentPlan p = newPlan(c, dto.getPeriodNo(), dto.getPlanDate(), dto.getPlanAmount(), dto.getRemindDays());
        planMapper.insert(p);
        return p.getId();
    }

    @Override
    @OperationLog(module = "回款", action = "生成计划", type = "CREATE")
    @Transactional
    public List<Long> generate(GeneratePlansDTO dto) {
        Contract c = requireContract(dto.getContractId());
        List<PaymentPlan> existing = listPlans(dto.getContractId());
        if (Boolean.TRUE.equals(dto.getOverwrite())) {
            assertNoSettled(existing, "该合同已有核销记录，不能覆盖原有计划");
            // 唯一键 uk_plan_contract_period 不含 is_deleted，软删后无法重建相同期数，故用物理删除
            planMapper.physicalDeleteByContract(dto.getContractId());
        } else if (!existing.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "该合同已存在回款计划，请使用覆盖模式或删除后重建");
        }

        BigDecimal total = c.getAmount();
        int periods = dto.getPeriods();
        BigDecimal each = total.divide(BigDecimal.valueOf(periods), 2, RoundingMode.HALF_UP);
        BigDecimal sum = each.multiply(BigDecimal.valueOf(periods));
        BigDecimal lastDelta = total.subtract(sum); // 最后一期补差，避免分摊误差

        List<Long> ids = new ArrayList<>();
        LocalDate date = dto.getFirstPlanDate();
        for (int i = 0; i < periods; i++) {
            PaymentPlan p = new PaymentPlan();
            p.setId(SnowflakeIdGenerator.next());
            p.setContractId(dto.getContractId());
            p.setPeriodNo(i + 1);
            p.setPlanDate(date);
            BigDecimal amt = (i == periods - 1) ? each.add(lastDelta) : each;
            p.setPlanAmount(amt);
            p.setSettledAmount(BigDecimal.ZERO);
            p.setUnsettledAmount(amt);
            p.setStatus("PENDING");
            p.setIsOverdue(0);
            p.setOverdueDays(0);
            p.setRemindDays(c.getRemindDays());
            planMapper.insert(p);
            ids.add(p.getId());
            date = nextDate(date, dto.getFrequency());
        }
        log.info("[plan] generated {} periods for contract {}", periods, dto.getContractId());
        return ids;
    }

    @Override
    @OperationLog(module = "回款", action = "更新计划", type = "UPDATE")
    @Transactional
    public void update(Long id, UpdatePlanDTO dto) {
        PaymentPlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.PM_PLAN_NOT_FOUND);
        }
        if ("SETTLED".equals(existing.getStatus())) {
            throw new BizException(ErrorCode.PM_PLAN_SETTLED);
        }
        if (existing.getSettledAmount() != null
                && dto.getPlanAmount().compareTo(existing.getSettledAmount()) < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "计划金额不能小于已核销金额 " + existing.getSettledAmount().toPlainString());
        }
        Contract c = requireContract(existing.getContractId());
        assertTotalEqualsContract(c, id, dto.getPlanAmount());

        BigDecimal diff = dto.getPlanAmount().subtract(existing.getPlanAmount());
        PaymentPlan upd = new PaymentPlan();
        upd.setId(id);
        upd.setVersion(dto.getVersion());
        upd.setPlanDate(dto.getPlanDate());
        upd.setPlanAmount(dto.getPlanAmount());
        upd.setRemindDays(dto.getRemindDays());
        // 同步 unsettled_amount，避免出现金额错位
        upd.setUnsettledAmount(existing.getUnsettledAmount().add(diff).max(BigDecimal.ZERO));
        boolean settled = existing.getSettledAmount() != null && existing.getSettledAmount().signum() > 0;
        upd.setStatus(upd.getUnsettledAmount().signum() == 0 ? "SETTLED" : (settled ? "PARTIAL" : "PENDING"));
        int rows = planMapper.updateById(upd);
        if (rows == 0) {
            throw new BizException(ErrorCode.PM_PLAN_NOT_FOUND, "已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @OperationLog(module = "回款", action = "删除计划", type = "DELETE")
    @Transactional
    public void remove(Long id) {
        PaymentPlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.PM_PLAN_NOT_FOUND);
        }
        if (existing.getSettledAmount() != null && existing.getSettledAmount().signum() > 0) {
            throw new BizException(ErrorCode.PM_PLAN_SETTLED, "已有核销记录，无法删除");
        }
        planMapper.deleteById(id);
    }

    @Override
    @OperationLog(module = "回款", action = "批量录入计划", type = "CREATE")
    @Transactional
    public List<Long> batchSave(BatchPlanDTO dto) {
        Contract c = requireContract(dto.getContractId());
        return savePlans(c, dto.getPlans(), Boolean.TRUE.equals(dto.getOverwrite()));
    }

    @Override
    @Transactional
    public List<Long> saveForContract(Long contractId, List<BatchPlanDTO.BatchPlanItem> plans, boolean overwrite) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        Contract c = requireContract(contractId);
        return savePlans(c, plans, overwrite);
    }

    @Override
    public PaymentPlanSummaryVO summary(Long contractId) {
        Contract c = requireContract(contractId);
        BigDecimal planned = planMapper.sumPlanAmountExcluding(contractId, null);
        if (planned == null) {
            planned = BigDecimal.ZERO;
        }
        PaymentPlanSummaryVO vo = new PaymentPlanSummaryVO();
        vo.setContractId(c.getId());
        vo.setContractCode(c.getCode());
        vo.setContractName(c.getName());
        vo.setContractAmount(c.getAmount());
        vo.setPlannedAmount(planned);
        vo.setDiff(c.getAmount().subtract(planned));
        vo.setPlanCount(planMapper.countByContract(contractId));
        vo.setComplete(vo.getDiff().signum() == 0);
        return vo;
    }

    @Override
    public void assertTotalEqualsContract(Long contractId, Long excludePlanId, BigDecimal incoming) {
        assertTotalEqualsContract(requireContract(contractId), excludePlanId, incoming);
    }

    @Override
    public int markOverdueAuto() {
        int rows = planMapper.markOverdue(LocalDate.now());
        if (rows > 0) {
            log.info("[scheduler] markOverdue affected {} plans", rows);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private Contract requireContract(Long contractId) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null) {
            throw new BizException(ErrorCode.CT_NOT_FOUND);
        }
        return c;
    }

    /**
     * 硬校验：该合同未删除计划合计（排除 excludePlanId，加上 incoming）必须恰好等于合同金额。
     */
    private void assertTotalEqualsContract(Contract c, Long excludePlanId, BigDecimal incoming) {
        BigDecimal total = planMapper.sumPlanAmountExcluding(c.getId(), excludePlanId);
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        if (incoming != null) {
            total = total.add(incoming);
        }
        if (total.compareTo(c.getAmount()) != 0) {
            throw new BizException(ErrorCode.PM_PLAN_AMOUNT_MISMATCH,
                    "回款计划合计 " + total.toPlainString() + " 与合同金额 "
                            + c.getAmount().toPlainString() + " 不一致，差额 "
                            + c.getAmount().subtract(total).toPlainString());
        }
    }

    private List<PaymentPlan> listPlans(Long contractId) {
        return planMapper.selectList(new QueryWrapper<PaymentPlan>().eq("contract_id", contractId));
    }

    private void assertNoSettled(List<PaymentPlan> plans, String message) {
        boolean settled = plans.stream().anyMatch(p ->
                p.getSettledAmount() != null && p.getSettledAmount().signum() > 0);
        if (settled) {
            throw new BizException(ErrorCode.PM_PLAN_SETTLED, message);
        }
    }

    private List<Long> savePlans(Contract c, List<BatchPlanDTO.BatchPlanItem> items, boolean overwrite) {
        // 1) 逐行字段校验 + 期数去重
        Set<Integer> periods = new LinkedHashSet<>();
        BigDecimal newSum = BigDecimal.ZERO;
        for (BatchPlanDTO.BatchPlanItem item : items) {
            if (!periods.add(item.getPeriodNo())) {
                throw new BizException(ErrorCode.PM_PLAN_PERIOD_DUPLICATE,
                        "第 " + item.getPeriodNo() + " 期重复");
            }
            if (item.getPlanDate() == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR,
                        "第 " + item.getPeriodNo() + " 期计划日期不能为空");
            }
            BigDecimal amt = item.getPlanAmount();
            if (amt == null || amt.signum() <= 0) {
                throw new BizException(ErrorCode.VALIDATION_ERROR,
                        "第 " + item.getPeriodNo() + " 期计划金额必须大于 0");
            }
            if (amt.stripTrailingZeros().scale() > 2) {
                throw new BizException(ErrorCode.VALIDATION_ERROR,
                        "第 " + item.getPeriodNo() + " 期计划金额最多支持 2 位小数");
            }
            newSum = newSum.add(amt);
        }

        // 2) 已有计划：存在核销记录时禁止覆盖
        List<PaymentPlan> existing = listPlans(c.getId());
        if (overwrite) {
            assertNoSettled(existing, "该合同已有核销记录，不能整批覆盖");
        } else {
            Map<Integer, PaymentPlan> byPeriod = new HashMap<>();
            for (PaymentPlan p : existing) {
                byPeriod.put(p.getPeriodNo(), p);
            }
            for (BatchPlanDTO.BatchPlanItem item : items) {
                PaymentPlan old = byPeriod.get(item.getPeriodNo());
                if (old != null && old.getSettledAmount() != null && old.getSettledAmount().signum() > 0
                        && item.getPlanAmount().compareTo(old.getSettledAmount()) < 0) {
                    throw new BizException(ErrorCode.PM_PLAN_SETTLED,
                            "第 " + item.getPeriodNo() + " 期已核销 " + old.getSettledAmount().toPlainString()
                                    + "，计划金额不能小于已核销金额");
                }
            }
        }

        // 3) 合计硬校验：落库后该合同计划合计必须恰好等于合同金额
        BigDecimal totalAfter = newSum;
        if (!overwrite) {
            BigDecimal kept = existing.stream()
                    .filter(p -> p.getPeriodNo() == null || !periods.contains(p.getPeriodNo()))
                    .map(PaymentPlan::getPlanAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalAfter = kept.add(newSum);
        }
        if (totalAfter.compareTo(c.getAmount()) != 0) {
            throw new BizException(ErrorCode.PM_PLAN_AMOUNT_MISMATCH,
                    "回款计划合计 " + totalAfter.toPlainString() + " 与合同金额 "
                            + c.getAmount().toPlainString() + " 不一致，差额 "
                            + c.getAmount().subtract(totalAfter).toPlainString());
        }

        // 4) 落库
        List<Long> ids = new ArrayList<>();
        if (overwrite) {
            planMapper.physicalDeleteByContract(c.getId());
            for (BatchPlanDTO.BatchPlanItem item : items) {
                PaymentPlan p = newPlan(c, item.getPeriodNo(), item.getPlanDate(),
                        item.getPlanAmount(), item.getRemindDays());
                planMapper.insert(p);
                ids.add(p.getId());
            }
        } else {
            Map<Integer, PaymentPlan> byPeriod = new HashMap<>();
            for (PaymentPlan p : listPlans(c.getId())) {
                byPeriod.put(p.getPeriodNo(), p);
            }
            for (BatchPlanDTO.BatchPlanItem item : items) {
                PaymentPlan old = byPeriod.get(item.getPeriodNo());
                if (old == null) {
                    PaymentPlan p = newPlan(c, item.getPeriodNo(), item.getPlanDate(),
                            item.getPlanAmount(), item.getRemindDays());
                    planMapper.insert(p);
                    ids.add(p.getId());
                } else {
                    PaymentPlan upd = new PaymentPlan();
                    upd.setId(old.getId());
                    upd.setVersion(old.getVersion());
                    upd.setPlanDate(item.getPlanDate());
                    upd.setPlanAmount(item.getPlanAmount());
                    upd.setRemindDays(item.getRemindDays() == null ? c.getRemindDays() : item.getRemindDays());
                    BigDecimal settled = old.getSettledAmount() == null ? BigDecimal.ZERO : old.getSettledAmount();
                    upd.setUnsettledAmount(item.getPlanAmount().subtract(settled).max(BigDecimal.ZERO));
                    upd.setStatus(upd.getUnsettledAmount().signum() == 0
                            ? "SETTLED" : (settled.signum() > 0 ? "PARTIAL" : "PENDING"));
                    if (planMapper.updateById(upd) == 0) {
                        throw new BizException(ErrorCode.SYS_ERROR, "并发冲突，第 " + item.getPeriodNo() + " 期保存失败");
                    }
                    ids.add(old.getId());
                }
            }
        }
        log.info("[plan] batch saved {} periods for contract {}, total={}",
                items.size(), c.getId(), totalAfter.toPlainString());
        return ids;
    }

    private PaymentPlan newPlan(Contract c, Integer periodNo, LocalDate planDate,
                                BigDecimal planAmount, Integer remindDays) {
        PaymentPlan p = new PaymentPlan();
        p.setId(SnowflakeIdGenerator.next());
        p.setContractId(c.getId());
        p.setPeriodNo(periodNo);
        p.setPlanDate(planDate);
        p.setPlanAmount(planAmount);
        p.setSettledAmount(BigDecimal.ZERO);
        p.setUnsettledAmount(planAmount);
        p.setStatus("PENDING");
        p.setIsOverdue(0);
        p.setOverdueDays(0);
        p.setRemindDays(remindDays == null ? c.getRemindDays() : remindDays);
        return p;
    }

    private LocalDate nextDate(LocalDate base, String freq) {
        return switch (freq) {
            case "MONTHLY" -> base.plusMonths(1);
            case "QUARTERLY" -> base.plusMonths(3);
            case "ONCE" -> base; // 单期不会进入下一次
            default -> base.plusMonths(1);
        };
    }

    private List<PaymentPlanVO> toVOs(List<PaymentPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        List<Long> contractIds = plans.stream()
                .map(PaymentPlan::getContractId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Contract> contracts = contractIds.isEmpty()
                ? Collections.emptyMap()
                : contractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(Contract::getId, c -> c));
        // VO 层即时计算 overdue/overdueDays，与 Dashboard / 账龄分析的口径对齐，
        // 避免 06:00 markOverdue 定时任务尚未跑完时列表里看不到「逾期」红 tag。
        // DB 字段 is_overdue / overdue_days 仍由 scheduler 维护，给筛选与提醒任务用。
        LocalDate today = LocalDate.now();
        return plans.stream().map(p -> {
            PaymentPlanVO vo = new PaymentPlanVO();
            BeanUtils.copyProperties(p, vo, "isOverdue", "overdueDays");
            boolean past = p.getPlanDate() != null && p.getPlanDate().isBefore(today);
            boolean unsettled = p.getUnsettledAmount() != null && p.getUnsettledAmount().signum() > 0;
            boolean realOverdue = past && unsettled && !"SETTLED".equals(p.getStatus());
            vo.setOverdue(realOverdue);
            vo.setOverdueDays(realOverdue
                    ? (int) java.time.temporal.ChronoUnit.DAYS.between(p.getPlanDate(), today)
                    : 0);
            Contract c = contracts.get(p.getContractId());
            if (c != null) {
                vo.setContractCode(c.getCode());
                vo.setContractName(c.getName());
            }
            return vo;
        }).toList();
    }
}
