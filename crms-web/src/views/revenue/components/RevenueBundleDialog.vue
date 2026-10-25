<template>
  <el-dialog
    :model-value="visible"
    title="整批录入收入（可同时录入回款计划）"
    width="min(1120px, 94vw)"
    top="6vh"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-form inline class="head">
      <el-form-item label="合同" required>
        <el-select
          v-model="contractId"
          filterable
          remote
          :remote-method="searchContract"
          :loading="contractLoading"
          placeholder="输入合同名称/编号"
          style="width: 320px"
          @change="onContractChange"
        >
          <el-option
            v-for="c in contractOptions"
            :key="c.id"
            :label="`${c.code} ${c.name}`"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="contractAmount !== null" label="合同金额">
        <strong>{{ money(contractAmount) }}</strong>
      </el-form-item>
      <el-form-item v-if="planSummary" label="已排计划">
        <span :class="planSummary.complete ? 'ok' : 'bad'">
          {{ money(planSummary.plannedAmount) }} / {{ money(planSummary.contractAmount) }}
        </span>
      </el-form-item>
    </el-form>

    <div class="cols">
      <div class="col-main">
        <div class="section-title">
          收入明细
          <el-button link type="primary" :icon="Plus" @click="addRevenueRow">新增一行</el-button>
        </div>
        <el-table :data="revenueRows" border size="small" max-height="280">
          <el-table-column label="确认日期" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.recognizeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="归属期间" width="120">
            <template #default="{ row }">
              <el-date-picker v-model="row.periodMonth" type="month" value-format="YYYY-MM" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-select v-model="row.incomeType" style="width: 100%">
                <el-option v-for="(meta, key) in IncomeTypeLabel" :key="key" :label="meta.label" :value="key" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="含税金额" min-width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="税率" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :max="1" :precision="4" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="成本" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.costAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单据号" width="140">
            <template #default="{ row }">
              <el-input v-model="row.voucherNo" maxlength="255" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link size="small" type="danger" @click="revenueRows.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title mt-2">
          回款计划（可留空，留空则沿用已有计划）
          <span>
            <el-button link type="primary" :icon="Plus" @click="addPlanRow">新增一期</el-button>
            <el-button link type="primary" @click="evenSplit">按合同金额均摊</el-button>
          </span>
        </div>
        <el-table :data="planRows" border size="small" max-height="240">
          <el-table-column label="期数" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.periodNo" :min="1" :controls="false" style="width: 80px" />
            </template>
          </el-table-column>
          <el-table-column label="计划日期" width="170">
            <template #default="{ row }">
              <el-date-picker v-model="row.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="计划金额" min-width="170">
            <template #default="{ row }">
              <el-input-number v-model="row.planAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="提醒天数" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.remindDays" :min="0" :max="365" :controls="false" style="width: 80px" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link size="small" type="danger" @click="planRows.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="col-side">
        <el-card shadow="never" class="panel">
          <div class="panel-title">校验预演</div>
          <div class="panel-row">
            <span>收入合计</span><strong>{{ money(revenueSum) }}</strong>
          </div>
          <div class="panel-row">
            <span>合同金额</span><strong>{{ money(contractAmount) }}</strong>
          </div>
          <div class="panel-row">
            <span>收入差额</span>
            <strong :class="revenueDiff === 0 ? 'ok' : 'bad'">{{ money(revenueDiff) }}</strong>
          </div>
          <el-divider />
          <div class="panel-row">
            <span>计划合计</span><strong>{{ money(planSum) }}</strong>
          </div>
          <div class="panel-row">
            <span>计划差额</span>
            <strong :class="planRows.length === 0 || planDiff === 0 ? 'ok' : 'bad'">
              {{ planRows.length === 0 ? '-' : money(planDiff) }}
            </strong>
          </div>
          <el-divider />
          <div class="panel-row">
            <span>已回款（截至今日）</span><strong>{{ money(balance?.receivedAmount) }}</strong>
          </div>
          <div class="panel-row">
            <span>后续计划待收</span><strong>{{ money(balance?.plannedUnsettled) }}</strong>
          </div>
          <div class="panel-row">
            <span>时点平衡差额</span>
            <strong :class="balance?.complete ? 'ok' : 'bad'">
              {{ balance ? money(balance.balanceDiff) : '-' }}
            </strong>
          </div>
          <div class="panel-tip">
            后端会按每条确认日期逐条校验：截至该日期的含税收入合计 = 该日期前实际回款 + 该日期后计划待收；
            且同一合同含税收入合计必须恰好等于合同金额。
          </div>
        </el-card>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" :loading="saving" @click="onSave">提交整批录入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { contractApi, type ContractVO } from '@/api/contract'
import { paymentPlanApi, type PaymentPlanSummaryVO } from '@/api/payment'
import {
  IncomeTypeLabel,
  revenueApi,
  type RevenueBundleDTO,
  type RevenueSummaryVO
} from '@/api/revenue'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

interface RevenueRow {
  recognizeDate: string
  periodMonth: string
  incomeType: string
  amount: number
  taxRate: number
  costAmount: number
  voucherNo: string
}

interface PlanRow {
  periodNo: number
  planDate: string
  planAmount: number
  remindDays: number
}

const contractId = ref('')
const contractAmount = ref<number | null>(null)
const contractOptions = ref<ContractVO[]>([])
const contractLoading = ref(false)
const planSummary = ref<PaymentPlanSummaryVO | null>(null)
const balance = ref<RevenueSummaryVO | null>(null)
const revenueRows = ref<RevenueRow[]>([])
const planRows = ref<PlanRow[]>([])
const saving = ref(false)

const revenueSum = computed(() =>
  round2(revenueRows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
)
const planSum = computed(() =>
  round2(planRows.value.reduce((s, r) => s + Number(r.planAmount || 0), 0))
)
const revenueDiff = computed(() =>
  round2(Number(contractAmount.value || 0) - revenueSum.value)
)
const planDiff = computed(() =>
  round2(Number(contractAmount.value || 0) - planSum.value)
)

const canSubmit = computed(() => {
  if (!contractId.value || revenueRows.value.length === 0) return false
  if (contractAmount.value === null) return false
  if (revenueDiff.value !== 0) return false
  if (planRows.value.length > 0 && planDiff.value !== 0) return false
  return true
})

watch(
  () => props.visible,
  (v) => {
    if (!v) return
    contractId.value = ''
    contractAmount.value = null
    contractOptions.value = []
    planSummary.value = null
    balance.value = null
    revenueRows.value = [newRevenueRow()]
    planRows.value = []
  }
)

function newRevenueRow(): RevenueRow {
  const today = new Date().toISOString().slice(0, 10)
  return {
    recognizeDate: today,
    periodMonth: today.slice(0, 7),
    incomeType: 'SERVICE',
    amount: 0,
    taxRate: 0,
    costAmount: 0,
    voucherNo: ''
  }
}

function addRevenueRow() {
  revenueRows.value.push(newRevenueRow())
}

function addPlanRow() {
  const next = planRows.value.reduce((max, r) => Math.max(max, Number(r.periodNo || 0)), 0) + 1
  planRows.value.push({
    periodNo: next,
    planDate: new Date().toISOString().slice(0, 10),
    planAmount: 0,
    remindDays: 7
  })
}

/** 按合同金额均摊：已有行则平摊到现有行，无行则自动创建 12 期。 */
function evenSplit() {
  const total = Number(contractAmount.value || 0)
  if (!total) {
    ElMessage.warning('请先选择合同')
    return
  }
  if (planRows.value.length === 0) {
    for (let i = 0; i < 12; i++) {
      const d = new Date()
      d.setMonth(d.getMonth() + i)
      planRows.value.push({
        periodNo: i + 1,
        planDate: d.toISOString().slice(0, 10),
        planAmount: 0,
        remindDays: 7
      })
    }
  }
  const n = planRows.value.length
  const each = Math.floor((total / n) * 100) / 100
  let assigned = 0
  planRows.value.forEach((r, i) => {
    if (i === n - 1) {
      r.planAmount = round2(total - assigned)
    } else {
      r.planAmount = each
      assigned = round2(assigned + each)
    }
  })
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

async function onContractChange(id: string) {
  contractAmount.value = null
  planSummary.value = null
  balance.value = null
  const picked = contractOptions.value.find((c) => c.id === id)
  if (picked) contractAmount.value = Number(picked.amount || 0)
  if (!id) return
  try {
    const [detail, summary] = await Promise.all([
      picked ? Promise.resolve(picked) : contractApi.detail(id),
      paymentPlanApi.summary(id)
    ])
    contractAmount.value = Number(detail.amount || 0)
    planSummary.value = summary
  } catch {
    /* ignore */
  }
}

async function onSave() {
  saving.value = true
  try {
    const payload: RevenueBundleDTO = {
      contractId: contractId.value,
      revenues: revenueRows.value.map((r) => ({
        contractId: contractId.value,
        periodMonth: r.periodMonth,
        recognizeDate: r.recognizeDate,
        incomeType: r.incomeType,
        amount: r.amount,
        taxRate: r.taxRate,
        costAmount: r.costAmount,
        voucherNo: r.voucherNo || undefined
      })),
      plans: planRows.value.length ? planRows.value : undefined,
      planOverwrite: true
    }
    balance.value = await revenueApi.bundle(payload)
    ElMessage.success('整批录入成功')
    emit('saved')
  } finally {
    saving.value = false
  }
}

function round2(v: number) {
  return Math.round(v * 100) / 100
}

function money(v: number | undefined | null) {
  return Number(v || 0).toLocaleString()
}
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.cols {
  display: flex;
  gap: 12px;
}
.col-main {
  flex: 1;
  min-width: 0;
}
.col-side {
  width: 280px;
  flex: none;
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 8px;
  font-weight: 600;
}
.panel-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.panel-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
  color: #606266;
}
.panel-tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
.ok {
  color: #67c23a;
}
.bad {
  color: #f56c6c;
}
.mt-2 {
  margin-top: 12px;
}
</style>
