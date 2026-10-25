<template>
  <div class="crms-page">
    <el-card class="crms-card" shadow="never">
      <el-form inline class="search-bar">
        <el-form-item label="合同">
          <el-select
            v-model="query.contractId"
            filterable
            remote
            clearable
            :remote-method="searchContract"
            :loading="contractLoading"
            placeholder="输入合同名称/编号"
            style="width: 260px"
            @change="onContractChange"
          >
            <el-option v-for="c in contractOptions" :key="c.id" :label="`${c.code} ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属期间">
          <el-date-picker
            v-model="query.periodMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="全部"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="收入类型">
          <el-select v-model="query.incomeType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(meta, key) in IncomeTypeLabel" :key="key" :label="meta.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="query.voucherNo" placeholder="单据号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-perm="'revenue:create'" type="primary" :icon="Plus" @click="openForm(null)">新增收入</el-button>
        <el-button v-perm="'revenue:create'" :icon="DocumentAdd" @click="bundleDialog = true">整批录入</el-button>
        <el-button v-perm="'revenue:import'" :icon="Upload" @click="importDialog = true">导入 Excel</el-button>
        <el-button :icon="Refresh" circle @click="loadAll" />
      </div>

      <el-descriptions v-if="summary" :column="6" border size="small" class="mb-2">
        <el-descriptions-item label="合同金额">{{ money(summary.contractAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已确认收入">{{ money(summary.confirmedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="合同差额">
          <span :class="summary.contractDiff === 0 ? 'ok' : 'bad'">{{ money(summary.contractDiff) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="已回款（截至今日）">{{ money(summary.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="后续计划待收">{{ money(summary.plannedUnsettled) }}</el-descriptions-item>
        <el-descriptions-item label="时点平衡差额">
          <span :class="summary.balanceDiff === 0 ? 'ok' : 'bad'">{{ money(summary.balanceDiff) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="summary && !summary.complete"
        type="warning"
        :closable="false"
        show-icon
        class="mb-2"
        title="该合同尚未满足「含税收入合计 = 合同金额」且「时点平衡」的校验条件"
      />

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column prop="contractCode" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="periodMonth" label="归属期间" width="100" align="center" />
        <el-table-column prop="recognizeDate" label="确认日期" width="110" />
        <el-table-column label="收入类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="IncomeTypeLabel[row.incomeType]?.type" size="small">
              {{ IncomeTypeLabel[row.incomeType]?.label || row.incomeType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="含税金额" width="130" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="110" align="right">
          <template #default="{ row }">{{ money(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column label="不含税" width="120" align="right">
          <template #default="{ row }">{{ money(row.netAmount) }}</template>
        </el-table-column>
        <el-table-column label="成本" width="110" align="right">
          <template #default="{ row }">{{ money(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="毛利" width="110" align="right">
          <template #default="{ row }">{{ money(row.grossProfit) }}</template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="单据号" width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-perm="'revenue:update'" link size="small" @click="openForm(row)">编辑</el-button>
            <el-popconfirm title="确认删除该收入明细？" @confirm="onDelete(row)">
              <template #reference>
                <el-button v-perm="'revenue:delete'" link size="small">
                  <span class="text-danger">删除</span>
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        background
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-2"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <RevenueFormDrawer v-model:visible="formDrawer" :record="editing" @saved="onSaved" />
    <RevenueBundleDialog v-model:visible="bundleDialog" @saved="onSaved" />
    <ImportExcelDialog
      v-model:visible="importDialog"
      title="批量导入收入明细"
      hint="Excel 列顺序：合同编号 / 收入归属期间 (YYYY-MM) / 确认日期 (yyyy-MM-dd) / 收入类型 (商品/服务/其他) / 含税金额 / 税率 (0~1) / 成本金额 / 单据号 / 备注。同一合同的全部行将作为整体校验。"
      :import-fn="revenueApi.importExcel"
      :download-template-fn="revenueApi.downloadTemplate"
      template-filename="收入明细导入模板.xlsx"
      @finished="loadAll"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentAdd, Plus, Refresh, Upload } from '@element-plus/icons-vue'
import { contractApi, type ContractVO } from '@/api/contract'
import {
  IncomeTypeLabel,
  revenueApi,
  type RevenueQuery,
  type RevenueRecordVO,
  type RevenueSummaryVO
} from '@/api/revenue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import RevenueFormDrawer from './components/RevenueFormDrawer.vue'
import RevenueBundleDialog from './components/RevenueBundleDialog.vue'

const query = reactive<RevenueQuery>({ page: 1, size: 20 })
const records = ref<RevenueRecordVO[]>([])
const total = ref(0)
const loading = ref(false)
const summary = ref<RevenueSummaryVO | null>(null)

const contractOptions = ref<ContractVO[]>([])
const contractLoading = ref(false)

const formDrawer = ref(false)
const editing = ref<RevenueRecordVO | null>(null)
const bundleDialog = ref(false)
const importDialog = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await revenueApi.list(query)
    records.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  if (!query.contractId) {
    summary.value = null
    return
  }
  try {
    summary.value = await revenueApi.summary(query.contractId)
  } catch {
    summary.value = null
  }
}

function loadAll() {
  load()
  loadSummary()
}

function onSearch() {
  query.page = 1
  load()
  loadSummary()
}

function onReset() {
  Object.assign(query, {
    contractId: undefined,
    periodMonth: undefined,
    incomeType: undefined,
    voucherNo: undefined,
    page: 1,
    size: 20
  })
  summary.value = null
  load()
}

async function onContractChange(id?: string) {
  query.page = 1
  if (id && !contractOptions.value.some((c) => c.id === id)) {
    try {
      contractOptions.value = [await contractApi.detail(id)]
    } catch {
      /* ignore */
    }
  }
  load()
  if (summary.value && summary.value.contractId !== id) summary.value = null
  loadSummary()
}

async function searchContract(keyword: string) {
  if (!keyword) {
    contractOptions.value = []
    return
  }
  contractLoading.value = true
  try {
    const res = await contractApi.list({ keyword, page: 1, size: 20 })
    contractOptions.value = res.items
  } finally {
    contractLoading.value = false
  }
}

function openForm(row: RevenueRecordVO | null) {
  editing.value = row
  formDrawer.value = true
}

function onSaved() {
  formDrawer.value = false
  bundleDialog.value = false
  loadAll()
}

async function onDelete(row: RevenueRecordVO) {
  await revenueApi.remove(row.id)
  ElMessage.success('已删除')
  loadAll()
}

function money(v: number | undefined) {
  return Number(v || 0).toLocaleString()
}

onMounted(loadAll)
</script>

<style scoped>
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin: 4px 0 12px;
  padding: 8px 0;

  :deep(.el-form-item) {
    margin: 0 !important;
  }
}

.toolbar {
  display: flex;
  gap: 8px;
  margin: 0 0 12px;
}

.mt-2 {
  margin-top: 12px;
}

.mb-2 {
  margin-bottom: 12px;
}

.ok {
  color: #67c23a;
}

.bad {
  color: #f56c6c;
}
</style>
