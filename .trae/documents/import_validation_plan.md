# 导入数据校验增强 实施计划

## 仓库调研结论

当前三类 Excel 批量导入服务的校验现状：

| 模块 | 已有校验 | 已知缺口 |
| --- | --- | --- |
| 客户 | 名称必填且 ≤100；类型枚举；统一社会信用代码正则；行业 ≤32；地区编码 ≤12；地址 ≤255；DB 捕获 USCC 重复 | 表头不校验；Excel 日期序列号不支持；金额无；空行计为失败；文件内重复 USCC 不报预校验错误 |
| 合同 | 名称必填且 ≤100；类型枚举；客户存在且唯一；金额 >0；日期必填且格式 yyyy-MM-dd；结束≥开始；提醒天数整数 | 表头不校验；日期序列号不支持；金额精度无限制（DECIMAL(18,2)）；备注无 1000 上限；空行计为失败 |
| 回款 | 合同编号存在；到账日期必填；金额 >0 | 表头不校验；日期序列号不支持；金额精度无限制；付款方/凭证号/备注无长度上限；文件内重复凭证号不拦截；空行计为失败 |

通用问题：
- 第一行被当作表头直接跳过，列顺序/列名错误时会静默产生错位数据。
- Excel 真实单元格的日期序列值（如 45678）无法解析。
- 无行数上限，超大文件可能导致 OOM 或事务超时。
- 完全空行会被计入失败行（应为跳过）。

## 文件与模块

- 新增 `crms-app/src/main/java/com/company/crms/common/util/ExcelImportHelper.java`：表头校验、灵活日期解析（含 Excel 序列号）、金额精度校验、长度校验、空行判断、`MAX_ROWS = 2000` 常量。
- 修改 `crms-app/src/main/java/com/company/crms/customer/service/impl/CustomerImportServiceImpl.java`：接入表头校验、行数校验、空行跳过、文件内 USCC 重复检测。
- 修改 `crms-app/src/main/java/com/company/crms/contract/service/impl/ContractImportServiceImpl.java`：接入表头校验、行数校验、空行跳过、金额精度与备注长度校验。
- 修改 `crms-app/src/main/java/com/company/crms/payment/service/impl/PaymentImportServiceImpl.java`：接入表头校验、行数校验、空行跳过、金额精度与字段长度校验、文件内凭证号重复检测。
- 修改 `crms-web/src/components/ImportExcelDialog.vue` 与 `PaymentImportDialog.vue`：在提示中补充“支持 Excel 日期格式、单次最多 2000 行”。
- 修改 `crms-app/src/test/java/com/company/crms/payment/PaymentImportTest.java`：补充表头错误、空行跳过、序列号日期、金额精度用例。

## 实施步骤（按依赖顺序）

1. 新建 `ExcelImportHelper`，提供静态方法：
   - `validateHeader(List<String> expected, Map<Integer,String> head, String moduleName)`：逐列比对（trim 后），不一致时抛 `IllegalArgumentException` 并列出期望列名。
   - `parseDateFlexible(String s, String label)`：依次尝试 `yyyy-MM-dd`、`yyyy/M/d`、Excel 序列号（`LocalDate.of(1899,12,30).plusDays(n)`），失败抛错。
   - `parseAmount(String s, String label)`：>0、至多 2 位小数、整数位 ≤16（DECIMAL(18,2)）。
   - `checkLength(String s, int max, String label)`。
   - `isEmptyRow(Map<Integer,String> row)`：所有非空列都为空。
   - `MAX_ROWS = 2000`。
2. 修改三类导入服务，在监听器重写 `invokeHeadMap` 校验表头；在 `invoke` 中：
   - 累计行数，超过 `MAX_ROWS` 抛错。
   - 空行直接 `return`（不计入 total）。
   - 用 Helper 替换原有 `parseDate`/`parseAmount`/`trim` 校验。
   - 客户：维护 `Set<String> seenUscc`，非空 USCC 重复时报错“文件内第 X 行已使用相同统一社会信用代码”。
   - 回款：维护 `Set<String> seenVoucher`，非空凭证号重复时报错。
3. 更新前端两个导入弹窗的提示文案（不改变交互）。
4. 补充单元测试并运行 `mvn test -pl` 相关用例、`pnpm build`。
5. 重启后端应用 Flyway（本次无 DDL 变更），用 Excel（含序列号日期、空行、重复行、超长字段）端到端验证三类导入回执。

## 依赖与注意事项

- 表头列名需与前端提示、测试 Excel 完全一致，避免“表头错误”误报。
- Excel 序列号解析假设工作簿使用 1900 日期系统（EasyExcel 默认）；若为 1904 系统会差 1462 天，当前不处理（公司内部模板统一 1900）。
- `invokeHeadMap` 中抛出的异常会被 EasyExcel 包装，需确认异常 message 能透传到 `ImportResultVO.errors` 或直接作为整体失败返回；若被吞则改为在 `invoke` 首行检查一个 `headerValid` 标志。
- 金额小数位校验采用 `new BigDecimal(s).scale() > 2`，对 `100.00` 等末尾零已规范化的值需用 `stripTrailingZeros().scale()` 判断。
- 行数上限选择 2000 行：兼顾业务体量与内存安全，可后续移至系统参数。

## 验证

- `mvn -DskipTests package` 编译通过；`mvn test` 中导入相关单测通过。
- `pnpm build` 通过。
- 构造覆盖以下场景的 Excel，分别调用三个 `/import` 接口并断言回执：
  1. 列名错误 → 整体失败，错误信息列出期望列。
  2. 含空行 → 空行不计入 total，有效行正常导入。
  3. 日期为 Excel 序列号 → 成功导入且日期正确。
  4. 金额 3 位小数 / 超长备注 → 该行失败，错误信息明确。
  5. 文件内重复 USCC / 凭证号 → 后续重复行失败。
  6. 超过 2000 行 → 整体失败。
- 浏览器验证三个“批量导入”弹窗提示文案更新、上传后回执正常。

## 风险

- **表头严格匹配误伤**：用户可能自行微调列名。处理：只做列数+列名 trim 相等校验，错误信息中给出完整期望列清单，便于用户对照模板；未来可提供模板下载。
- **EasyExcel 异常透传**：若 `invokeHeadMap` 异常被吞，会出现“表头错误但无提示”。处理：改用 `headerValid` 标志在 `invoke` 首行抛出，确保进入 `ImportResultVO` 的 errors 或直接抛业务异常由全局异常处理返回。
- **序列号日期与字符串日期混淆**：纯数字字符串可能被误判为序列号。处理：仅当字符串可解析为整数且值在 Excel 合理区间（25569–60000，即 1970–2034）时才按序列号解析，否则按日期格式字符串解析。
