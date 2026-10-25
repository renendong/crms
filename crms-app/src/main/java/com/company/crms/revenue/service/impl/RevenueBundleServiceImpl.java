package com.company.crms.revenue.service.impl;

import com.company.crms.common.annotation.OperationLog;
import com.company.crms.common.exception.BizException;
import com.company.crms.common.exception.ErrorCode;
import com.company.crms.contract.entity.Contract;
import com.company.crms.contract.mapper.ContractMapper;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.revenue.dto.RevenueBundleDTO;
import com.company.crms.revenue.entity.RevenueRecord;
import com.company.crms.revenue.mapper.RevenueRecordMapper;
import com.company.crms.revenue.service.RevenueBalanceValidator;
import com.company.crms.revenue.service.RevenueBundleService;
import com.company.crms.revenue.service.RevenueRecordService;
import com.company.crms.revenue.support.RevenueRules;
import com.company.crms.revenue.vo.RevenueSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueBundleServiceImpl implements RevenueBundleService {

    private final RevenueRecordMapper revenueMapper;
    private final ContractMapper contractMapper;
    private final PaymentPlanService planService;
    private final RevenueBalanceValidator balanceValidator;
    private final RevenueRecordService revenueRecordService;

    @Override
    @OperationLog(module = "收入确认", action = "整批录入收入", type = "CREATE")
    @Transactional
    public RevenueSummaryVO saveBundle(RevenueBundleDTO dto) {
        Contract c = contractMapper.selectById(dto.getContractId());
        if (c == null) {
            throw new BizException(ErrorCode.CT_NOT_FOUND);
        }

        // 1) 先落回款计划（内部完成「计划合计 = 合同金额」硬校验），使后续时点平衡基于最新计划
        if (dto.getPlans() != null && !dto.getPlans().isEmpty()) {
            planService.saveForContract(dto.getContractId(), dto.getPlans(),
                    Boolean.TRUE.equals(dto.getPlanOverwrite()));
        }

        // 2) 构造收入实体并派生税额 / 不含税 / 毛利
        List<RevenueRecord> records = dto.getRevenues().stream()
                .map(RevenueRules::fromDTO)
                .toList();
        for (RevenueRecord r : records) {
            if (!dto.getContractId().equals(r.getContractId())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR,
                        "收入明细的合同必须与整批录入的合同一致");
            }
        }

        // 3) 时点平衡 + 合同级收入合计硬校验
        balanceValidator.validateContract(dto.getContractId(), records, null);

        // 4) 逐条落库
        records.forEach(revenueMapper::insert);
        log.info("[revenue] bundle saved {} records for contract {}", records.size(), dto.getContractId());
        return revenueRecordService.summary(dto.getContractId());
    }
}
