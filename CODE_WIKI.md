# CRMS 合同回款管理系统 — Code Wiki

> 版本：v1.0.0 ｜ 最后更新：2026-10-25
>
> 本文档为研发视角的代码维基，涵盖项目整体架构、模块职责、关键类与函数、依赖关系、数据库设计与运行部署方式。

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [技术栈与依赖](#3-技术栈与依赖)
4. [后端模块详解](#4-后端模块详解)
   - 4.1 [common 公共能力](#41-common-公共能力)
   - 4.2 [iam 身份与权限](#42-iam-身份与权限)
   - 4.3 [customer 客户档案](#43-customer-客户档案)
   - 4.4 [contract 合同管理](#44-contract-合同管理)
   - 4.5 [payment 回款管理](#45-payment-回款管理)
   - 4.6 [notification 通知中心](#46-notification-通知中心)
   - 4.7 [report 报表看板](#47-report-报表看板)
   - 4.8 [system 系统管理](#48-system-系统管理)
   - 4.9 [file 文件存储](#49-file-文件存储)
5. [前端架构](#5-前端架构)
6. [数据库设计](#6-数据库设计)
7. [关键横切机制](#7-关键横切机制)
8. [项目运行方式](#8-项目运行方式)
9. [部署与运维](#9-部署与运维)
10. [代码约定与扩展指南](#10-代码约定与扩展指南)

---

## 1. 项目概述

CRMS（Contract Receivable Management System，合同回款管理系统）是一套面向企业的合同与回款全流程管理一体化平台，覆盖业务链路：

```
客户档案 → 合同状态机 → 回款计划 → 自动核销 → 账龄分析 → 看板报表
```

核心能力：

- **客户档案管理**：客户、联系人、合并、查重、导入导出
- **合同全生命周期**：状态机（草稿→生效→完成/终止/过期）、附件、备注
- **回款管理**：计划/实际/自动核销/红冲/账龄分桶
- **通知中心**：合同到期、回款临期/逾期提醒，用户偏好设置
- **报表看板**：KPI 看板、月度趋势、账龄环图、TOP 客户、我的待办
- **系统管理**：用户/角色/权限/部门、系统参数、操作日志、回收站
- **安全合规**：Sa-Token JWT、AES-256-GCM 加密、数据范围隔离、软删除+硬删除审计

---

## 2. 整体架构

### 2.1 系统拓扑

```
┌──────────────────────────────────────────────────────────────┐
│                        Nginx (80/443)                         │
│  ┌──────────────────┐         ┌──────────────────────────┐   │
│  │  前端静态资源     │  ──→    │  /api/v1/* 反向代理       │   │
│  │  (crms-web dist) │         │  → app1:8080 / app2:8080  │   │
│  └──────────────────┘         └──────────┬───────────────┘   │
└───────────────────────────────────────────┼───────────────────┘
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    │                       │                       │
              ┌─────▼─────┐          ┌──────▼──────┐        ┌──────▼──────┐
              │  app1     │          │   app2      │        │  MinIO      │
              │  crms-app │◄────────►│  crms-app   │        │  文件存储    │
              │  :8080    │          │  :8080      │        │  :9000       │
              └─────┬─────┘          └──────┬──────┘        └─────────────┘
                    │                       │
              ┌─────▼───────────────────────▼──────┐
              │          MySQL 8.0  (3306)          │
              │          + Flyway 迁移              │
              └─────────────────────────────────────┘
                    │                       │
              ┌─────▼───────────────────────▼──────┐
              │          Redis 7.2  (6379)          │
              │  (Sa-Token 会话 / 缓存 / ShedLock)  │
              └─────────────────────────────────────┘
```

### 2.2 后端分层架构

后端采用经典的 Controller → Service → Mapper 三层架构，配合 DTO/VO 分层传输：

```
Controller (REST API + 权限注解)
    │  DTO (入参校验 @Valid)
    ▼
Service (业务逻辑 + 事务 + 状态机)
    │  Entity (MyBatis-Plus)
    ▼
Mapper (MyBatis-Plus BaseMapper + 自定义 XML)
    │
    ▼
MySQL
```

- **DTO**（Data Transfer Object）：接收前端请求参数，带 `@Valid` 校验
- **VO**（View Object）：返回给前端的视图对象
- **Entity**：数据库实体，继承 `BaseEntity`（含审计字段、软删除、乐观锁）

### 2.3 前端架构

```
┌─────────────────────────────────────────────┐
│  App.vue                                     │
│  ├── router/ (路由守卫 + 权限校验)            │
│  ├── stores/ (Pinia: auth)                   │
│  ├── api/ (Axios 封装 + 各模块 API)           │
│  ├── layouts/ (MainLayout 主框架)             │
│  ├── views/ (按业务模块划分页面)               │
│  │   ├── dashboard/  customer/  contract/    │
│  │   ├── payment/    report/    notification/│
│  │   └── system/                             │
│  └── components/ (通用组件)                   │
└─────────────────────────────────────────────┘
```

---

## 3. 技术栈与依赖

### 3.1 后端核心依赖

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 17 | 运行时 |
| 框架 | Spring Boot | 3.2.5 | 应用骨架 |
| ORM | MyBatis-Plus | 3.5.6 | 数据库操作 |
| 认证 | Sa-Token | 1.38.0 | JWT 登录鉴权 |
| 数据库迁移 | Flyway | (boot 管理) | DDL 版本化 |
| 缓存 | Spring Data Redis | — | 会话/缓存/分布式锁 |
| 调度锁 | ShedLock | 5.13.0 | 多实例定时任务互斥 |
| 文件存储 | MinIO SDK | 8.5.10 | 对象存储 |
| Excel | EasyExcel | 3.3.4 | 导入导出 |
| 文档 | Springdoc OpenAPI | 2.3.0 | API 文档 |
| 工具 | Hutool | 5.8.27 | 通用工具 |
| 对象映射 | MapStruct | 1.5.5 | DTO↔VO 转换 |
| 简化 | Lombok | 1.18.32 | 样板代码消除 |
| 测试 | Testcontainers | 1.19.7 | 集成测试 |

### 3.2 前端核心依赖

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Vue | 3.4.27 | UI 框架 |
| 路由 | Vue Router | 4.3.2 | 前端路由 |
| 状态 | Pinia | 2.1.7 | 状态管理 |
| UI 库 | Element Plus | 2.7.3 | 组件库 |
| HTTP | Axios | 1.7.2 | 请求封装 |
| 图表 | ECharts | 5.5.0 | 数据可视化 |
| 日期 | Day.js | 1.11.11 | 日期处理 |
| 构建 | Vite | 5.2.13 | 构建工具 |
| 语言 | TypeScript | 5.4.5 | 类型系统 |
| 测试 | Vitest | 1.6.0 | 单元测试 |

### 3.3 基础设施

| 组件 | 版本 | 端口 | 用途 |
|------|------|------|------|
| MySQL | 8.0 | 3306 | 主数据库 |
| Redis | 7.2 | 6379 | 缓存/会话/锁 |
| MinIO | 8.5+ | 9000 | 文件对象存储 |
| Nginx | 1.25 | 80/443 | 反向代理+HTTPS |

---

## 4. 后端模块详解

后端包结构：`com.company.crms.{module}`，每个模块遵循 `controller / service / mapper / entity / dto / vo / enums` 子包约定。

### 4.1 common 公共能力

**路径**：`crms-app/src/main/java/com/company/crms/common/`

提供全项目共享的基础设施，所有业务模块均依赖此包。

#### 4.1.1 响应与异常

| 类 | 职责 |
|----|------|
| `Result<T>` | 统一响应封装 `{code, message, data, traceId}`，成功码 `0` |
| `PageResult<T>` | 分页响应 `{items, total, page, size}` |
| `ImportResultVO` | 批量导入结果 `{total, success, failed, errors}` |
| `ErrorCode` | 业务错误码枚举（模块两位+序号三位，如 `CT-002`） |
| `BizException` | 业务异常，携带 `ErrorCode` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理 |

`GlobalExceptionHandler` 处理策略：
- `BizException` → HTTP 200 + 业务错误码
- `NotLoginException` → 401
- `NotPermissionException` / `NotRoleException` → 403
- 参数校验异常 → 400
- 兜底异常 → 500，屏蔽堆栈，仅暴露 `traceId`

#### 4.1.2 实体基类

`BaseEntity` — 所有业务实体的抽象父类：

| 字段 | 注解 | 说明 |
|------|------|------|
| `id` | `@TableId(ASSIGN_ID)` | 雪花 ID（MyBatis-Plus 内置） |
| `createdBy` / `createdAt` | `FieldFill.INSERT` | 创建审计 |
| `updatedBy` / `updatedAt` | `FieldFill.INSERT_UPDATE` | 更新审计 |
| `isDeleted` | `@TableLogic` | 逻辑删除（0/1） |
| `version` | `@Version` | 乐观锁 |

由 `MyMetaObjectHandler` 在插入/更新时自动填充审计字段。

#### 4.1.3 安全与加密

| 类 | 职责 |
|----|------|
| `AesCryptoUtil` | AES-256-GCM 加解密，密钥 32 字节，输出 `Base64(IV‖密文‖tag)` |
| `SensitiveField` | 注解，标注敏感字段及脱敏策略（PHONE/EMAIL/NAME/ID_CARD） |
| `MaskUtil` | 脱敏展示工具，如 `138****1234`、`z****@x.com` |
| `UserContext` | 当前用户上下文（userId、deptIds、dataScope、permissions、superAdmin） |
| `UserContextHolder` | ThreadLocal 持有器，请求开始注入、结束清理 |

加密字段落库流程：实体标注 `@SensitiveField` → MyBatis 类型处理器加密写入 → 读取时透明解密 → Jackson 序列化时按 `mask` 脱敏输出。

#### 4.1.4 数据范围隔离

`DataScopeInterceptor`（MyBatis-Plus 内部拦截器）：

- 仅对 Mapper 方法 ID 后缀为 `WithDataScope` 的 SELECT 生效
- 根据 `UserContext.dataScope` 自动追加 SQL 条件：
  - `ALL` / 超管：不追加
  - `DEPT`：追加 `dept_id IN (?, ?, ...)`
  - `SELF`：追加 `owner_id = ?`
- 使用 jsqlparser 解析并重写 WHERE 子句

#### 4.1.5 操作日志 AOP

`OperationLogAspect`（`@Aspect`）：

- 拦截标注 `@OperationLog(module, action, type, recordParams)` 的方法
- 记录：操作人、IP、URI、模块、动作、参数（JSON）、结果、耗时、错误信息
- 通过 `OperationLogWriter.write()` 异步写入（`@Async`）`operation_log` 表

#### 4.1.6 ID 与编号生成

| 类 | 职责 |
|----|------|
| `SnowflakeIdGenerator` | 简化雪花算法，`CRMS_NODE_ID` 环境变量区分实例 |
| `CodeGenerator` | 业务编号生成：客户 `CU-yyyyMM-NNNN`、合同 `HT-yyyyMM-NNNN`，基于 Redis INCR，按月切换 Key |

#### 4.1.7 配置类

| 类 | 职责 |
|----|------|
| `SaTokenConfig` | Sa-Token 拦截器，`/api/v1/**` 需登录（排除登录/Swagger/健康检查） |
| `SaTokenPermissionImpl` | Sa-Token 权限接口实现，从 `UserContext.permissions` 取权限 |
| `MybatisPlusConfig` | 注册分页插件 + `DataScopeInterceptor` |
| `WebMvcConfig` | CORS、静态资源、拦截器注册 |
| `AsyncConfig` | 异步线程池（操作日志写入用） |
| `CacheConfig` | Spring Cache + Redis 序列化配置 |
| `ShedLockConfig` | 分布式调度锁（Redis provider） |
| `JacksonConfig` | Long→String 序列化（防 JS 大整数精度丢失）、时间格式 |
| `OpenApiConfig` | Springdoc 全局信息 |
| `MinioProperties` | MinIO 配置属性绑定 |

### 4.2 iam 身份与权限

**路径**：`crms-app/src/main/java/com/company/crms/iam/`

负责认证、用户、角色、权限、部门管理，是系统的安全基石。

#### 核心服务

| 服务 | 职责 |
|------|------|
| `AuthService` | 登录/登出/获取当前用户/改密/二次密码校验 |
| `UserAdminService` | 用户 CRUD、分配角色、启用/禁用、重置密码 |
| `RoleAdminService` | 角色 CRUD、分配权限 |
| `PermissionAdminService` | 权限点管理 |
| `DepartmentAdminService` | 部门树管理 |
| `UserContextLoader` | 装载用户完整上下文（角色+权限+部门集合+数据范围），Redis 缓存 5 分钟 |

#### 关键流程

**登录流程**：
1. `AuthService.login(req, ip)` 校验用户名密码（BCrypt）
2. 失败次数累计，达 5 次锁定 15 分钟（`failed_count` / `locked_until`）
3. 登录成功 → Sa-Token 签发 UUID Token，Redis 存储会话
4. 返回 `LoginVO{token, mustChangePassword}`

**权限校验**：
- 接口级：`@SaCheckPermission("customer:create")`
- 数据级：`DataScopeInterceptor` 自动追加数据范围条件
- 超管（`super_admin=1`）：跳过数据范围限制

**密码策略**：
- BCrypt 散列存储
- 最小长度 8 位
- 首登强制改密（`must_change_pwd=1`）

### 4.3 customer 客户档案

**路径**：`crms-app/src/main/java/com/company/crms/customer/`

管理客户档案与联系人，是合同的上游实体。

#### 核心服务

| 服务 | 职责 |
|------|------|
| `CustomerService` | 客户 CRUD、软/硬删除、停用/启用、合并、查重、详情聚合 |
| `CustomerContactService` | 联系人 CRUD |
| `CustomerImportService` | Excel 批量导入客户 |

#### `CustomerService` 关键方法

| 方法 | 说明 |
|------|------|
| `page(query)` | 分页查询（支持关键词、状态、负责人筛选，数据范围保护） |
| `detail(id)` | 客户详情 |
| `create(dto)` | 新建客户（自动生成编号、USCC 唯一性校验） |
| `merge(dto)` | 客户合并：将被合并客户的合同/联系人/附件 `customer_id` 改写为目标，被合并方标记 `MERGED` |
| `checkDuplicate(name, uscc, selfId)` | 按名称/USCC 查重（排除自身和已合并客户） |
| `aggregate(id)` | 聚合详情：基础信息 + 联系人 + 最近合同 + 变更日志 |
| `softDelete(id)` / `hardDelete(id, reason)` | 软删除入回收站 / 硬删除（仅超管，写 `hard_delete_log`） |

#### 实体

`Customer` 关键字段：`code`(编号)、`name`、`uscc`(统一社会信用代码，唯一索引)、`type`、`level`、`ownerId`、`deptId`、`status`(ACTIVE/DISABLED/MERGED)、`mergedTo`(合并目标)。

`CustomerContact`：`customerId`、`name`、`phone`、`email`、`wechat`（后三者 AES 加密）。

### 4.4 contract 合同管理

**路径**：`crms-app/src/main/java/com/company/crms/contract/`

管理合同全生命周期，核心是**合同状态机**。

#### 合同状态机

`ContractStatus` 枚举定义状态流转规则：

```
DRAFT ──▶ EFFECTIVE ──▶ COMPLETED
   │           │
   ▼           ▼
TERMINATED  TERMINATED
              │
              ▼
           EXPIRED  (定时任务在到期日自动迁移)
```

`assertTransition(from, to)` 校验流转合法性，非法抛出 `BizException(CT_STATUS_INVALID)`。

- `DRAFT` → 可转 `EFFECTIVE` / `TERMINATED`
- `EFFECTIVE` → 可转 `COMPLETED` / `TERMINATED` / `EXPIRED`
- `COMPLETED` / `TERMINATED` / `EXPIRED` → 终态，不可转

#### 核心服务

| 服务 | 职责 |
|------|------|
| `ContractService` | 合同 CRUD、状态流转、自动到期标记、到期查询 |
| `ContractAttachmentService` | 附件上传/下载/删除（关联 `file_object`） |
| `ContractNoteService` | 合同备注 |
| `ContractImportService` | Excel 批量导入合同 |

#### `ContractService` 关键方法

| 方法 | 说明 |
|------|------|
| `transition(id, to, reason)` | 状态机流转，写 `change_log` |
| `markExpiredAuto()` | 定时任务调用，自动将过到期日的 `EFFECTIVE` 合同迁为 `EXPIRED` |
| `dueSoon(advanceDays)` | 查询未来 N 天内到期的合同（用于提醒） |

#### 实体

`Contract` 关键字段：`code`(编号)、`name`、`type`、`customerId`、`amount`、`signedAt`、`performStartAt`、`performEndAt`(履行到期日)、`remindDays`、`ownerId`、`deptId`、`status`。

### 4.5 payment 回款管理

**路径**：`crms-app/src/main/java/com/company/crms/payment/`

系统最复杂的业务模块，涵盖回款计划、实际回款、自动核销、红冲、账龄分析。

#### 核心服务

| 服务 | 职责 |
|------|------|
| `PaymentPlanService` | 回款计划 CRUD、生成计划、标记逾期 |
| `PaymentRecordService` | 实际回款记录 CRUD、触发核销 |
| `SettlementService` | **自动核销算法**（核心） |
| `RedReverseService` | **红冲**（反向核销） |
| `AgingService` | **账龄分桶**与钻取 |
| `PaymentImportService` | 回款记录 Excel 导入 |

#### 自动核销算法（`SettlementService`）

核心算法步骤：
1. 对实际回款记录加数据库行锁
2. 取合同下未结清的回款计划（按 `plan_date` 升序）并加行锁
3. 逐条扣减 `unsettled_amount`，剩余金额回写 `unallocated_amount`
4. 同步更新 `plan.status` 为 `PARTIAL` / `SETTLED`
5. 插入 `payment_settlement` 关联记录

支持手工指定 `targetPlanIds`（非空时只在该集合内核销），与自动按时间序逻辑互斥。

#### 红冲（`RedReverseService`）

红冲流程：
1. 原 record 状态置为 `REVERSED`
2. 新增 `status=RED` 的负数金额 record，引用原 record id
3. 反查原 record 关联的全部 settlement，逐条按"反向核销"重算 `plan.settled_amount`
4. 写 `change_log` + 通知财务负责人

#### 账龄分析（`AgingService`）

账龄桶划分（以 `today` 为基准日）：

| 桶 | 范围 |
|----|------|
| 未到期 | `plan_date >= today` |
| 0-30 天 | `0 <= overdueDays <= 30` |
| 31-60 天 | `31 <= overdueDays <= 60` |
| 61-90 天 | `61 <= overdueDays <= 90` |
| 90+ 天 | `overdueDays > 90` |

对每条 `unsettled_amount > 0` 的 `PaymentPlan` 计算 `overdueDays = today - plan_date`，按桶聚合金额。`drill()` 方法支持钻取某个桶下的未结清计划列表。

#### 定时任务（`PaymentScheduler`）

| Cron | 任务 |
|------|------|
| `0 0 6 * * ?` | 标记逾期：`plan_date < today` 且未结清 → `is_overdue=1` |
| `0 30 9 * * ?` | 回款临期提醒（默认提前 7 天） |
| `0 45 9 * * ?` | 回款逾期提醒 |

所有任务均加 `@SchedulerLock`，确保多实例部署时只执行一次。

### 4.6 notification 通知中心

**路径**：`crms-app/src/main/java/com/company/crms/notification/`

#### 核心服务

| 服务 | 职责 |
|------|------|
| `NotificationService` | 通知列表/未读数/标记已读/归档/偏好设置 |
| `NotificationDispatcher` | 通知分发：合同到期、回款临期、回款逾期 |
| `ReminderScheduler` | 合同到期提醒调度 |

#### 通知场景

- 合同到期（默认提前 30 天）
- 回款计划临期（默认提前 7 天）
- 回款计划逾期
- 客户合并
- 红冲操作

每条通知按 `(receiver_id, scene, biz_id)` 去重，避免重复提醒。用户可通过 `NotificationSetting` 配置各场景的接收偏好。

### 4.7 report 报表看板

**路径**：`crms-app/src/main/java/com/company/crms/report/`

#### 核心服务 `ReportService`

| 方法 | 说明 |
|------|------|
| `dashboard()` | 综合 KPI 看板（合同总额、已回款、待回款、逾期金额等） |
| `monthlyTrend(months)` | 月度合同/回款趋势（默认最近 12 个月） |
| `aging(today)` | 应收账龄分桶 |
| `topCustomers(n, metric)` | TOP N 客户（PAID/UNPAID/CONTRACT 维度） |
| `myTodos(contractAdvance, paymentAdvance)` | 我的待办：合同到期/回款临期/回款逾期 |
| `exportExcel(name, response)` | 报表 Excel 导出 |
| `warmupCache()` / `evictCache()` | 缓存预热/失效，由 `ReportCacheScheduler` 调度 |

报表接口结果通过 Spring Cache + Redis 缓存，`ReportCacheScheduler` 定时预热。

### 4.8 system 系统管理

**路径**：`crms-app/src/main/java/com/company/crms/system/`

#### 核心服务

| 服务 | 职责 |
|------|------|
| `SystemParamService` | 系统参数 CRUD（键值对，如提醒天数、附件上限等） |
| `OperationLogQueryService` | 操作日志查询 |
| `ChangeLogService` | 业务变更日志记录与查询（单字段变更追踪） |
| `HardDeleteLogService` | 硬删除审计日志 |
| `RecycleBinService` | 回收站：列出软删除记录、恢复、彻底删除 |

#### 变更日志（`ChangeLogService`）

`record(bizType, bizId, field, oldValue, newValue, reason)` — 记录业务实体字段级变更，支持时间线回溯。

#### 回收站机制

- 软删除：`is_deleted=1`，进入回收站，可恢复
- 硬删除：从数据库物理删除，写 `hard_delete_log`，仅超管可执行

### 4.9 file 文件存储

**路径**：`crms-app/src/main/java/com/company/crms/file/`

#### `FileStorage` 接口

抽象文件存储，支持两种实现：
- **LocalFileStorage**：本地磁盘存储（默认，`./uploads`）
- **MinIO 实现**：MinIO/S3 兼容对象存储（生产多实例推荐）

| 方法 | 说明 |
|------|------|
| `save(bizType, file)` | 上传文件，返回 objectKey |
| `load(objectKey)` | 读取文件流 |
| `delete(objectKey)` | 删除文件 |
| `previewUrl(fileObjectId, expireSeconds)` | 生成预览/下载 URL |
| `bucket()` | 桶/根目录名称 |

通过配置 `crms.storage.type` 切换 `local` / `minio`。

---

## 5. 前端架构

### 5.1 目录结构

```
crms-web/src/
├── api/              # API 层
│   ├── http.ts       # Axios 封装（拦截器、token、错误处理）
│   ├── auth.ts       # 认证 API
│   ├── customer.ts   # 客户 API
│   ├── contract.ts   # 合同 API
│   ├── payment.ts    # 回款 API
│   ├── iam.ts        # 用户/角色/部门 API
│   ├── notification.ts
│   ├── report.ts
│   ├── system.ts
│   └── types.ts      # 通用类型
├── stores/
│   └── auth.ts       # Pinia 认证 store
├── router/
│   └── index.ts      # 路由 + 守卫
├── layouts/
│   └── MainLayout.vue
├── views/            # 页面（按业务模块）
├── components/       # 通用组件
└── utils/            # 工具（format/permission/enum）
```

### 5.2 HTTP 封装（`http.ts`）

- `baseURL: '/api/v1'`，超时 30s
- 请求拦截器：自动注入 `Authorization: Bearer {token}`
- 响应拦截器：
  - `code === '0'` → 成功，返回 `data`
  - 401 → 清除 token，跳转登录
  - 403 → 提示"权限不足"
  - 其他 → 弹出错误消息
- 分页响应自动将 `total`（字符串）转回 `Number`，避免 Element Plus 分页组件警告

### 5.3 路由守卫（`router/index.ts`）

`beforeEach` 守卫逻辑：
1. 公开路由（登录、404）直接放行
2. 未登录 → 跳转 `/login`（带 redirect）
3. 未加载用户信息 → 调用 `fetchMe()`
4. 强制改密用户 → 跳转改密页
5. 路由有 `perm` 元信息 → 校验权限，无权限跳转 404

### 5.4 认证 Store（`stores/auth.ts`）

状态：`token`、`user`、`permissions`(Set)、`loaded`

| Action | 说明 |
|--------|------|
| `login(username, password)` | 登录并获取用户信息 |
| `fetchMe()` | 获取当前用户 + 权限集合 |
| `logout()` | 登出并重置 |
| `hasPermission(code)` | 权限校验（超管放行） |

### 5.5 通用组件

| 组件 | 用途 |
|------|------|
| `ImportExcelDialog.vue` | Excel 导入弹窗（客户/合同/回款复用） |
| `NotificationBell.vue` | 顶栏通知铃铛 |
| `PasswordPromptDialog.vue` | 高危操作二次密码确认 |
| `EmptyHint.vue` | 空状态提示 |

---

## 6. 数据库设计

### 6.1 ER 关系概览

```
iam_department ||--o{ iam_user
iam_user       ||--o{ iam_user_role
iam_role       ||--o{ iam_user_role
iam_role       ||--o{ iam_role_permission
iam_permission ||--o{ iam_role_permission

customer       ||--o{ customer_contact
customer       ||--o{ contract
contract       ||--o{ contract_attachment
contract       ||--o{ contract_note
contract       ||--o{ payment_plan
contract       ||--o{ payment_record
payment_plan   ||--o{ payment_settlement
payment_record ||--o{ payment_settlement

iam_user       ||--o{ notification
iam_user       ||--o{ notification_setting
```

### 6.2 模块与表归属

| 模块 | 表 |
|------|-----|
| IAM | `iam_user` `iam_role` `iam_permission` `iam_user_role` `iam_role_permission` `iam_department` |
| 客户 | `customer` `customer_contact` |
| 合同 | `contract` `contract_attachment` `contract_note` |
| 回款 | `payment_plan` `payment_record` `payment_settlement` |
| 通知 | `notification` `notification_setting` |
| 系统 | `change_log` `operation_log` `hard_delete_log` `system_param` `file_object` |

### 6.3 关键索引

- `customer.uscc` UNIQUE — 防止统一信用代码重复
- `payment_plan(contract_id, period_no)` UNIQUE — 同合同内期次唯一
- `notification(receiver_id, scene, biz_id, created_at)` — 通知去重查询
- `operation_log(operator_id, created_at)` 和 `(module, op_type, created_at)` — 审计回溯
- `contract.perform_end_at` — 到期提醒批处理快速命中

### 6.4 软删除约定

除 `payment_settlement`、`iam_user_role`、`iam_role_permission`、`operation_log`、`hard_delete_log`、`system_param`、`change_log`、`iam_permission` 外，所有表均含 `is_deleted` 字段，由 MyBatis-Plus `@TableLogic` 自动过滤。

### 6.5 加密存储字段

| 表 | 字段 | 存储 | 显示 |
|----|------|------|------|
| `iam_user` | `phone` | AES-256-GCM | `138****1234` |
| `iam_user` | `email` | AES-256-GCM | `z****@x.com` |
| `customer_contact` | `phone` `email` `wechat` | AES-256-GCM | 同上 |
| `payment_record` | `voucher_no` | AES-256-GCM | 仅财务可见明文 |

### 6.6 Flyway 迁移版本

| 版本 | 内容 |
|------|------|
| V1.0.0 | 初始化 DDL（所有表结构） |
| V1.0.1 | 权限点种子数据 |
| V1.0.2 | 字典与管理员账号种子 |
| V1.0.3 | 系统参数种子 |
| V1.0.4 | 修复管理员密码 |
| V1.0.5 | 合同附件数量上限参数 |
| V1.0.6 | 加宽 file_object 与 operation_log 字段 |
| V1.0.7 | 导入权限种子 |

---

## 7. 关键横切机制

### 7.1 统一响应格式

所有 API 返回 `Result<T>`：
```json
{ "code": "0", "message": "OK", "data": {...}, "traceId": "..." }
```
分页返回 `PageResult<T>`：
```json
{ "items": [...], "total": 100, "page": 1, "size": 20 }
```

### 7.2 错误码规范

`ErrorCode` 枚举，命名：**模块两位 + 序号三位**：

| 前缀 | 模块 | 示例 |
|------|------|------|
| `SYS` | 系统通用 | `SYS-500` 系统错误 |
| `AUTH` | 鉴权 | `AUTH-401` 未登录 |
| `IAM` | 身份管理 | `IAM-001` 用户名或密码错误 |
| `CU` | 客户 | `CU-002` 统一社会信用代码已存在 |
| `CT` | 合同 | `CT-002` 合同状态不允许此操作 |
| `PM` | 回款 | `PM-004` 红冲金额非法 |
| `NT` | 通知 | `NT-001` 通知不存在 |
| `FL` | 文件 | `FL-003` 文件大小超出限制 |
| `SY` | 系统管理 | `SY-002` 无硬删除权限 |

### 7.3 审计追踪

三层审计机制：
1. **操作日志**（`operation_log`）：`@OperationLog` AOP 记录接口级操作，异步写入
2. **变更日志**（`change_log`）：业务字段级变更，记录 oldValue/newValue，支持时间线
3. **硬删除日志**（`hard_delete_log`）：物理删除审计，记录删除人、原因、时间

### 7.4 分布式调度

`@EnableSchedulerLock` + ShedLock（Redis provider），多实例部署时定时任务只执行一次：
- `PaymentScheduler`：逾期标记、临期/逾期提醒
- `ReminderScheduler`：合同到期提醒
- `ReportCacheScheduler`：报表缓存预热

### 7.5 TraceId 链路追踪

`TraceIdFilter` 在请求入口生成 `traceId` 写入 MDC，`Result` 响应中携带 `traceId`，便于日志检索。

### 7.6 大整数处理

后端 `Long` 经 Jackson 序列化为字符串，前端 `http.ts` 响应拦截器自动将分页 `total` 转回 `Number`，避免 JS 大整数精度丢失。

---

## 8. 项目运行方式

### 8.1 前置依赖

- Java 17+ / Maven 3.9+
- Node 20+ / pnpm 9+
- Docker 24+（用于 MySQL/Redis/MinIO）

### 8.2 本地开发启动

```bash
# 1) 启动基础设施（MySQL、Redis、MinIO）
cd deploy && cp .env.example .env
docker compose -f docker-compose.test.yml up -d mysql redis minio

# 2) 启动后端（Flyway 自动建表 + 写入种子数据）
cd ../crms-app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3) 启动前端
cd ../crms-web
pnpm install
pnpm dev
```

默认账号：`admin` / `Admin@123456`（首登强制改密）

### 8.3 后端配置项

`application.yml` 核心配置：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8080 | 服务端口 |
| `crms.security.aes-key` | 32 字符 | AES-256 密钥（生产用环境变量覆盖） |
| `crms.security.login-max-failed` | 5 | 最大登录失败次数 |
| `crms.security.login-lock-minutes` | 15 | 锁定时长（分钟） |
| `crms.storage.type` | local | 存储类型：local / minio |
| `crms.reminder.contract-due-days` | 30 | 合同到期提前提醒天数 |
| `crms.reminder.payment-due-days` | 7 | 回款临期提前提醒天数 |

### 8.4 前端开发环境

`.env.development` 配置 Vite 代理，`/api` 转发到 `http://localhost:8080`。

### 8.5 API 文档

后端启动后访问：
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

导出 OpenAPI 并生成前端 TS 客户端：
```bash
cd scripts && ./export-openapi.sh localhost:8080
cd ../crms-web && pnpm gen:api
```

### 8.6 运行测试

```bash
# 后端单测 + Testcontainers 集成测试
cd crms-app
./mvnw test

# 前端单测
cd crms-web
pnpm test:run
```

### 8.7 验收脚本

```bash
./scripts/acceptance.sh localhost:8080
```

---

## 9. 部署与运维

### 9.1 一键部署（生产/演示）

```bash
curl -fsSL https://github.com/liudi2991/crms/releases/download/v1.0.2/crms-installer-v1.0.2.tar.gz -o crms-installer.tar.gz
tar xzf crms-installer.tar.gz && cd crms-installer-v1.0.2
sudo ./install.sh
```

### 9.2 Docker 编排

生产环境 `docker-compose.prod.yml` 采用双应用实例 + Nginx 负载均衡：

| 服务 | 说明 |
|------|------|
| `app1` / `app2` | 两个后端实例，通过 `CRMS_NODE_ID` 区分 |
| `nginx` | 反向代理 + HTTPS，托管前端静态资源 |

### 9.3 环境变量

关键环境变量（见 `deploy/.env.example`）：

| 变量 | 说明 |
|------|------|
| `MYSQL_*` | MySQL 连接信息 |
| `REDIS_*` | Redis 连接信息 |
| `MINIO_*` | MinIO 连接信息 + 公网 endpoint |
| `CRMS_AES_KEY` | AES-256 主密钥（32 字符） |
| `CRMS_NODE_ID` | 实例节点 ID（0-1023） |
| `SPRING_PROFILES_ACTIVE` | 运行环境：local / prod |

### 9.4 容器镜像构建

后端 Dockerfile 多阶段构建：Maven 编译 → JRE 运行，含阿里云 Maven mirror、非 root 用户、健康检查。
前端 Dockerfile：Node 构建 → Nginx 托管静态资源。

### 9.5 CI/CD

`deploy/Jenkinsfile` 多分支流水线：
- `feature/*`：lint + 单测
- `develop`：lint + 单测 + 构建 + 推送测试环境
- `tag`：人工审批后部署生产 + smoke test

### 9.6 运维脚本

`deploy/scripts/`：
- `check_app.sh` — 应用健康检查
- `check_disk.sh` — 磁盘空间巡检
- `backup.sh` — 数据库备份
- `rollback.sh` — 回滚脚本
- `letsencrypt.sh` — HTTPS 证书申请/续期

---

## 10. 代码约定与扩展指南

### 10.1 后端分层约定

```
controller  →  @RestController + @SaCheckPermission + @Valid
service     →  interface + impl，事务 @Transactional
mapper      →  extends BaseMapper<T>，自定义方法后缀 WithDataScope 启用数据范围
entity      →  extends BaseEntity + @TableName
dto         →  入参，@NotBlank/@NotNull 等校验
vo          →  返回前端的视图
```

### 10.2 新增业务模块步骤

1. 在 `com.company.crms` 下新建模块包，按 `controller/service/mapper/entity/dto/vo` 组织
2. Entity 继承 `BaseEntity`，Mapper 继承 `BaseMapper`
3. Controller 标注 `@RequestMapping("/api/v1/xxx")` 和 `@Tag`
4. 需数据范围保护的 Mapper 方法命名以 `WithDataScope` 结尾
5. 需审计的操作标注 `@OperationLog`
6. 在 Flyway 新增迁移脚本建表

### 10.3 前端新增页面步骤

1. 在 `views/` 下新建页面组件
2. 在 `api/` 下新建对应 API 模块
3. 在 `router/index.ts` 注册路由，配置 `meta.perm` 权限码
4. 在 `MainLayout` 菜单中添加入口（如需）

### 10.4 权限码规范

格式：`模块:操作`，如 `customer:create`、`contract:list`、`payment:red_reverse`、`system:manage`。

### 10.5 敏感字段处理

1. 实体字段标注 `@SensitiveField(mask = SensitiveField.Mask.PHONE)`
2. 数据库字段类型用 `VARCHAR` 足够容纳加密后 Base64 长度
3. VO 返回时自动脱敏展示，明文仅在内部处理时可用

### 10.6 测试约定

- 后端：JUnit 5 + Testcontainers（MySQL），关键算法（核销、账龄、红冲、状态机）必须有单测
- 前端：Vitest + @vue/test-utils，工具函数需有单测
- 测试类命名：`{被测类}Test`

---

> 本文档基于仓库代码自动分析生成，如有疑问请查阅源码或对应的 SRS/DSS 文档。
