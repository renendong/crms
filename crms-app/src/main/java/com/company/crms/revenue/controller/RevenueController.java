package com.company.crms.revenue.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.company.crms.common.response.ImportResultVO;
import com.company.crms.common.response.PageResult;
import com.company.crms.common.response.Result;
import com.company.crms.common.util.ExcelImportHelper;
import com.company.crms.revenue.dto.CreateRevenueDTO;
import com.company.crms.revenue.dto.RevenueBundleDTO;
import com.company.crms.revenue.dto.RevenueQuery;
import com.company.crms.revenue.dto.UpdateRevenueDTO;
import com.company.crms.revenue.service.RevenueBundleService;
import com.company.crms.revenue.service.RevenueImportService;
import com.company.crms.revenue.service.RevenueRecordService;
import com.company.crms.revenue.vo.RevenueRecordVO;
import com.company.crms.revenue.vo.RevenueSummaryVO;
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

@Tag(name = "收入确认")
@RestController
@RequestMapping("/api/v1/revenues")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueRecordService service;
    private final RevenueBundleService bundleService;
    private final RevenueImportService importService;

    @Operation(summary = "收入明细列表（分页）")
    @SaCheckPermission("revenue:list")
    @GetMapping
    public Result<PageResult<RevenueRecordVO>> list(@Valid RevenueQuery query) {
        return Result.ok(service.page(query));
    }

    @Operation(summary = "收入明细详情")
    @SaCheckPermission("revenue:list")
    @GetMapping("/{id}")
    public Result<RevenueRecordVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "合同级收入校验面板")
    @SaCheckPermission("revenue:list")
    @GetMapping("/summary/{contractId}")
    public Result<RevenueSummaryVO> summary(@PathVariable Long contractId) {
        return Result.ok(service.summary(contractId));
    }

    @Operation(summary = "新增收入明细（合计必须等于合同金额）")
    @SaCheckPermission("revenue:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreateRevenueDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "整批录入收入（可同时录入回款计划）")
    @SaCheckPermission("revenue:create")
    @PostMapping("/bundle")
    public Result<RevenueSummaryVO> bundle(@Valid @RequestBody RevenueBundleDTO dto) {
        return Result.ok(bundleService.saveBundle(dto));
    }

    @Operation(summary = "修改收入明细")
    @SaCheckPermission("revenue:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateRevenueDTO dto) {
        service.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除收入明细")
    @SaCheckPermission("revenue:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }

    @Operation(summary = "Excel 批量导入收入明细")
    @SaCheckPermission("revenue:import")
    @PostMapping("/import")
    public Result<ImportResultVO> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(importService.importRevenues(file));
    }

    @Operation(summary = "下载收入明细导入模板")
    @SaCheckPermission("revenue:import")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        String filename = URLEncoder.encode("收入明细导入模板.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ExcelImportHelper.writeTemplate(response.getOutputStream(),
                importService.getTemplateHeaders(), "收入明细导入模板");
    }
}
