package com.company.crms.revenue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.common.response.PageResult;
import com.company.crms.common.util.SnowflakeIdGenerator;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.customer.entity.Customer;
import com.company.crms.customer.mapper.CustomerMapper;
import com.company.crms.payment.mapper.PaymentPlanMapper;
import com.company.crms.payment.mapper.PaymentRecordMapper;
import com.company.crms.revenue.dto.CreateRevenueDTO;
import com.company.crms.revenue.dto.RevenueQuery;
import com.company.crms.revenue.dto.UpdateRevenueDTO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import com.company.crms.revenue.service.RevenueRecordService;
import com.company.crms.revenue.support.RevenueRules;
import com.company.crms.revenue.vo.RevenueRecordVO;
import com.company.crms.revenue.vo.RevenueSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueRecordServiceImpl implements RevenueRecordService {

    private final RevenueRecordMapper revenueMapper;
    private final ContractMapper contractMapper;
    private final CustomerMapper customerMapper;
    private final PaymentRecordMapper recordMapper;
    private final PaymentPlanMapper planMapper;
    private final RevenueBalanceValidator balanceValidator;

    @Override
    public PageResult<RevenueRecordVO> page(RevenueQuery query) {
        Page<RevenueRecord> page = Page.of(query.getPage(), query.getSize());
        QueryWrapper<RevenueRecord> w = new QueryWrapper<>();
        if (query.getContractId() != null) w.eq("contract_id", query.getContractId());
        if (query.getPeriodMonth() != null && !query.getPeriodMonth().isBlank()) {
            w.eq("period_month", query.getPeriodMonth().trim());
        }
        if (query.getIncomeType() != null && !query.getIncomeType().isBlank()) {
            w.eq("income_type", query.getIncomeType().trim());
        }
        if (query.getVoucherNo() != null && !query.getVoucherNo().isBlank()) {
            w.like("voucher_no", query.getVoucherNo().trim());
        }
        if (query.getFromDate() != null) w.ge("recognize_date", query.getFromDate());
        if (query.getToDate() != null) w.le("recognize_date", query.getToDate());
        w.orderByDesc("recognize_date").orderByDesc("id");

        Page<RevenueRecord> result = (Page<RevenueRecord>) revenueMapper.selectPage(page, w);
        return PageResult.of(result, toVOs(result.getRecords()));
    }

    @Override
    public RevenueRecordVO detail(Long id) {
        RevenueRecord r = revenueMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.RV_NOT_FOUND);
        }
        return toVOs(List.of(r)).get(0);
    }

    @Override
    @OperationLog(module = "收入确认", action = "新增收入明细", type = "CREATE")
    @Transactional
    public Long create(CreateRevenueDTO dto) {
        RevenueRecord r = build(dto);
        balanceValidator.validateContract(dto.getContractId(), List.of(r), null);
        revenueMapper.insert(r);
        log.info("[revenue] created {} contract={} amount={}", r.getId(), r.getContractId(), r.getAmount());
        return r.getId();
    }

    @Override
    @OperationLog(module = "收入确认", action = "修改收入明细", type = "UPDATE")
    @Transactional
    public void update(Long id, UpdateRevenueDTO dto) {
        RevenueRecord existing = revenueMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RV_NOT_FOUND);
        }
        RevenueRecord r = build(dto);
        r.setId(id);
        r.setVersion(dto.getVersion());
        balanceValidator.validateContract(dto.getContractId(), List.of(r), id);
        if (revenueMapper.updateById(r) == 0) {
            throw new BizException(ErrorCode.RV_NOT_FOUND, "已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @OperationLog(module = "收入确认", action = "删除收入明细", type = "DELETE")
    @Transactional
    public void remove(Long id) {
        RevenueRecord existing = revenueMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RV_NOT_FOUND);
        }
        revenueMapper.deleteById(id);
    }

    @Override
    public RevenueSummaryVO summary(Long contractId) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null) {
            throw new BizException(ErrorCode.CT_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        BigDecimal confirmed = nvl(revenueMapper.sumAmountByContract(contractId, null));
        BigDecimal received = nvl(recordMapper.sumEffectiveAmountUpTo(contractId, today));
        BigDecimal futurePlan = nvl(planMapper.sumUnsettledAfter(contractId, today));

        RevenueSummaryVO vo = new RevenueSummaryVO();
        vo.setContractId(c.getId());
        vo.setContractCode(c.getCode());
        vo.setContractName(c.getName());
        vo.setContractAmount(c.getAmount());
        vo.setConfirmedAmount(confirmed);
        vo.setContractDiff(c.getAmount().subtract(confirmed));
        vo.setReceivedAmount(received);
        vo.setPlannedUnsettled(futurePlan);
        vo.setBalanceDiff(confirmed.subtract(received.add(futurePlan)));
        vo.setComplete(vo.getContractDiff().signum() == 0 && vo.getBalanceDiff().signum() == 0);
        return vo;
    }

    private RevenueRecord build(CreateRevenueDTO dto) {
        return RevenueRules.fromDTO(dto);
    }

    private List<RevenueRecordVO> toVOs(List<RevenueRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> contractIds = records.stream()
                .map(RevenueRecord::getContractId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Contract> contracts = contractIds.isEmpty()
                ? Collections.emptyMap()
                : contractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(Contract::getId, c -> c));
        List<Long> customerIds = contracts.values().stream()
                .map(Contract::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> customerNames = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName));

        return records.stream().map(r -> {
            RevenueRecordVO vo = new RevenueRecordVO();
            BeanUtils.copyProperties(r, vo);
            Contract c = contracts.get(r.getContractId());
            if (c != null) {
                vo.setContractCode(c.getCode());
                vo.setContractName(c.getName());
                if (c.getCustomerId() != null) {
                    vo.setCustomerName(customerNames.get(c.getCustomerId()));
                }
            }
            return vo;
        }).toList();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
