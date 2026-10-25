package com.company.crms.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.response.PageResult;
import com.company.crms.common.response.Result;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.payment.dto.BatchPlanDTO;
import com.company.crms.payment.dto.CreatePlanDTO;
import com.company.crms.payment.dto.GeneratePlansDTO;
import com.company.crms.payment.dto.PlanQuery;
import com.company.crms.payment.dto.UpdatePlanDTO;
import com.company.crms.payment.service.PaymentPlanImportService;
import com.company.crms.payment.service.PaymentPlanService;
import com.company.crms.payment.vo.PaymentPlanSummaryVO;
import com.company.crms.payment.vo.PaymentPlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "回款计划")
@RestController
@RequestMapping("/api/v1/payment-plans")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService service;
    private final PaymentPlanImportService importService;

    @Operation(summary = "回款计划列表（分页）")
    @SaCheckPermission("payment:plan")
    @GetMapping
    public Result<PageResult<PaymentPlanVO>> list(@Valid PlanQuery query) {
        return Result.ok(service.page(query));
    }

    @Operation(summary = "合同下的回款计划")
    @SaCheckPermission("payment:plan")
    @GetMapping("/by-contract/{contractId}")
    public Result<List<PaymentPlanVO>> byContract(@PathVariable Long contractId) {
        return Result.ok(service.listByContract(contractId));
    }

    @Operation(summary = "新增回款计划")
    @SaCheckPermission("payment:plan")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreatePlanDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "批量生成回款计划")
    @SaCheckPermission("payment:plan")
    @PostMapping("/generate")
    public Result<List<Long>> generate(@Valid @RequestBody GeneratePlansDTO dto) {
        return Result.ok(service.generate(dto));
    }

    @Operation(summary = "修改回款计划")
    @SaCheckPermission("payment:plan")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdatePlanDTO dto) {
        service.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除回款计划")
    @SaCheckPermission("payment:plan")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }

    @Operation(summary = "批量录入回款计划（合计必须等于合同金额）")
    @SaCheckPermission("payment:plan")
    @PostMapping("/batch")
    public Result<List<Long>> batch(@Valid @RequestBody BatchPlanDTO dto) {
        return Result.ok(service.batchSave(dto));
    }

    @Operation(summary = "合同级回款计划汇总（差额提示）")
    @SaCheckPermission("payment:plan")
    @GetMapping("/summary/{contractId}")
    public Result<PaymentPlanSummaryVO> summary(@PathVariable Long contractId) {
        return Result.ok(service.summary(contractId));
    }

    @Operation(summary = "Excel 批量导入回款计划")
    @SaCheckPermission("payment:plan_import")
    @PostMapping("/import")
    public Result<ImportResultVO> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(importService.importPlans(file));
    }

    @Operation(summary = "下载回款计划导入模板")
    @SaCheckPermission("payment:plan_import")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        String filename = URLEncoder.encode("回款计划导入模板.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ExcelImportHelper.writeTemplate(response.getOutputStream(),
                importService.getTemplateHeaders(), "回款计划导入模板");
    }
}
