# v1.0.2

> 一键安装包 + 一系列上线后的生产 bug 修复 + 大量 UI 美化。

## 亮点：开箱即用的部署体验

新增 [`install.sh`](../install.sh) 一键安装入口。在一台干净的 Ubuntu/Debian 云主机上：

```bash
curl -fsSL https://github.com/liudi2991/crms/releases/download/v1.0.2/crms-installer-v1.0.2.tar.gz \
  -o crms-installer.tar.gz
tar xzf crms-installer.tar.gz && cd crms-installer-v1.0.2
sudo ./install.sh
```

约 10 分钟后浏览器访问 `http://<你的IP>/`，默认账号 `admin` / `Admin@12345`。

`install.sh` 会自动：
- 装 Docker + docker compose（已装则跳过）
- 部署源码到 `/opt/crms`
- 探测公网 IP，生成 32 字节 AES 主密钥与所有数据库密码写入 `.env`（权限 600）
- 配置 `MINIO_PUBLIC_ENDPOINT` 让浏览器能直接下载附件
- ufw 自动放行 80/9000，云安全组提示用户手动放行
- buildx 多阶段构建后端 + 前端镜像（不需要宿主机有 JDK / Node）
- `docker compose up -d` 拉起 MySQL / Redis / MinIO / 后端 / Nginx 全栈
- 等待 healthcheck 通过后输出登录信息

支持 `install / upgrade / status / uninstall` 四个子命令，详见 [INSTALL.md](../INSTALL.md)。

## 修复

- **fix(minio)**: 附件下载在公网失败 — 后端拆 `MinioClient` 双 bean，预签名 URL 走公网 endpoint（`MINIO_PUBLIC_ENDPOINT`），不再把容器内 `http://minio:9000` 写到浏览器 URL 里。
- **fix(report)**: 报表中心 Excel 导出 401 — 前端 `fetch` header 由废弃的 `satoken` 改为 Sa-Token 配置的 `Authorization: Bearer`。
- **fix(web)**: ElPagination 废弃警告 — 后端 `total` 返回 string 时由响应拦截器自动转 number。
- **fix(web)**: 报表中心切 tab 时 ECharts "缩在左侧很小"，必须按 F12 才正常 — 用 `ResizeObserver` 等容器有尺寸再 init，并在 tab 切换时 `chart.resize()`。Dashboard / 账龄分析 同步加固。
- **fix(deploy)**: 持久化 `/app/uploads` — 之前本地存储模式下容器重建会带走附件。
- **fix(perm)**: 销售角色 R01 不再看到「账龄分析 / 报表中心」菜单，且 Dashboard 不再因后端 403 弹错误 toast。
- **fix(detail-pages)**: keep-alive 下跨路由切换时 watcher 串到旧组件导致"详情页返回报错"、"附件页转圈圈"。
- **fix(customer/contract)**: 列表页负责人/部门/客户列由数字 ID 改为枚举回填的中文名。
- **fix(deploy-remote)**: 服务器 `.env` 永远不被本地覆盖。

## UI 美化

- 全局 list/detail 页 tier-A 视觉打磨：筛选条、按钮、tag、空态、时间格式
- 侧栏二级菜单缩进修正、菜单图标补齐、与顶部 logo 在同一垂直基线对齐
- 登录页重做、品牌一致化（"合同回款管理系统"）
- 综合看板 KPI 卡片重新分两行布局
- Dashboard / 账龄分析 / 报表中心 三处 ECharts 视觉语言统一

## 新增：回款计划合计校验与收入确认（后续迭代）

> 随 Flyway `V1.0.8__payment_plan_total_and_revenue.sql` 上线。

### 回款计划合计硬校验

- 同一合同回款计划金额合计 **必须恰好等于合同金额**，否则报 `PM-002` 并拒绝保存（单行录入 / 批量录入 / 批量生成 / Excel 导入全部生效）。
- 新增单行录入弹窗（`+ 新增计划`）与批量录入弹窗，实时显示「合同金额 / 已排合计 / 差额」，差额 ≠ 0 时禁止保存。
- 新增 `GET /api/v1/payment-plans/summary/{contractId}` 合同计划汇总（`plannedAmount / diff / complete`）。
- 新增回款计划导入 `POST /api/v1/payment-plans/import` + 模板下载；模板 6 列 `合同编号 / 期数 / 计划日期 / 计划金额 / 提醒天数 / 备注`，**按合同整组原子**写入。
- 新增权限点 `payment:plan_import`（R01 销售、R05 超管）。

### 实际回款按期数核销

- `payment_record` 新增 `period_no`；登记实际回款时期数 **必填**（`PM-008`）。
- 按「合同 + 期数」定位唯一计划并加行锁核销（`settleByPeriod`），允许部分核销；超出该期待收的部分记入「未分配金额」，**不溢出到下一期**。
- 期数不存在报 `PM-001`，该期已结清报 `PM-003`。
- 回款 Excel 模板改为 7 列（新增「期数」，位于第 2 列）；前端回款列表新增「期数」筛选与列，登记抽屉的期数下拉展示「第 N 期 · 计划日期 · 待核销 X」。

### 收入确认模块

- 新菜单「收入确认」（`#/revenues`），权限点 `revenue:list / create / update / delete / import`（R01 查看，R02/R04 查看，R03 财务全功能，R05 全功能）。
- 新增 `revenue_record` 表：收入归属期间、确认日期、收入类型、含税金额、税率、税额、不含税金额、成本金额、毛利、单据号、备注。
- 金额自动派生：税额 = 含税 × 税率（HALF_UP 2 位）、不含税 = 含税 − 税额、毛利 = 不含税 − 成本。
- **时点平衡校验 `RV-004`**：截至确认日期 T 的含税收入合计 = T 之前（含 T）的实际回款 + T 之后（不含 T）的计划待收；实际回款只统计 `NORMAL`，计划待收取「未核销金额」。
- **合同级硬校验 `RV-002`**：同一合同含税收入合计必须恰好等于合同金额。
- 前置校验 `RV-005`：合同必须已排完整回款计划（合计 = 合同金额，否则 `PM-002`）。
- 「整批录入」支持收入明细与回款计划同录（可「按合同金额均摊」生成 12 期），单事务保存：先写计划 → 再校验 → 再写收入。
- 收入导入模板 9 列 `合同编号 / 收入归属期间 / 确认日期 / 收入类型 / 含税金额 / 税率 / 成本金额 / 单据号 / 备注`，按合同整组原子写入；期间格式 `YYYY-MM` 且不得晚于当前月（`RV-003`）。

### 数据库与测试

- `V1.0.8__payment_plan_total_and_revenue.sql`：新增 `revenue_record` 表（19 列 + 4 索引）、`payment_record.period_no` 列与 `idx_record_contract_period`、权限点 `payment:plan_import` 与 `revenue:*`、角色授权。
- 新增 8 个测试类共 68 个用例；全量 `mvn test` 137 个用例通过。

## 升级方法

**已经在跑 v1.0.0 的实例**，建议这样升级：

```bash
# 服务器上
cd /opt/crms && git pull
cd deploy && docker compose -f docker-compose.single.yml down
cd .. && ./scripts/build-images.sh 1.0.2
cd deploy && IMAGE_TAG=1.0.2 docker compose -f docker-compose.single.yml up -d
```

或者直接：

```bash
sudo /opt/crms/install.sh upgrade
```

**注意**：升级到 v1.0.2 后必须在 `/opt/crms/deploy/.env` 中配置 `MINIO_PUBLIC_ENDPOINT=http://<你的公网IP>:9000` 并放行 `9000/tcp`，否则附件下载仍然不可用。

## 完整 changelog

- 79fb652 feat(installer): 一键安装包 install.sh + GitHub Release 发布脚本
- 7ae3a6e fix(minio): 支持双 endpoint，让浏览器能下载预签名附件
- 1d429b5 fix(deploy): 持久化 crms-app 本地附件目录
- be89608 fix(report): 修复导出 Excel 401
- 42fa8df fix(web): 修复 ElPagination 废弃警告与报表中心图表渲染
- 7c224f4 style(charts): unify chart visual language
- 854292d style(dashboard): redesign KPI grid
- aee4e17 style(layout): align sidebar logo with menu items
- 8ce2b21 style(layout,login): unify branding
- 4beea99 style(layout): polish sidebar menu structure
- 4c7ca2d style(ui): tier-A polish across list/detail pages
- 0a2820f fix(perm): hide aging/report menus from R01 sales
- e68b8d8 fix(dashboard): gate panels by permission
- e24bbd9 fix(detail-pages): guard against keep-alive watcher leaks
- 4c78573 fix(customer/contract): show owner/dept/customer names

