<template>
  <div>
    <div class="toolbar">
      <el-button v-perm="'payment:plan'" type="primary" :icon="MagicStick" @click="genDialog = true">
        生成计划
      </el-button>
      <el-button v-perm="'payment:plan'" :icon="Plus" @click="openPlanForm(null)">
        新增一期
      </el-button>
      <el-button v-perm="'payment:plan'" :icon="Tickets" @click="batchDialog = true">
        批量录入
      </el-button>
      <el-button v-perm="'payment:record'" :icon="Money" @click="recordDrawer = true">
        登记回款
      </el-button>
      <el-button :icon="Refresh" circle @click="loadAll" />
    </div>

    <el-alert
      v-if="planSummary"
      :type="planSummary.complete ? 'success' : 'error'"
      :closable="false"
      show-icon
      class="mb-2"
    >
      合同金额 {{ money(planSummary.contractAmount) }}，已排计划合计 {{ money(planSummary.plannedAmount) }}
      （{{ planSummary.planCount }} 期），差额 <strong>{{ money(planSummary.diff) }}</strong>
      <template v-if="!planSummary.complete">：同一合同回款计划合计必须恰好等于合同金额</template>
    </el-alert>

    <EmptyHint v-if="!loading && !rows.length" description="暂无回款计划，可点击右上角『生成计划』" />
    <el-table v-else v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="periodNo" label="期" width="60" align="center" />
      <el-table-column label="计划日期" width="120">
        <template #default="{ row }">{{ formatDate(row.planDate) }}</template>
      </el-table-column>
      <el-table-column label="计划金额" width="140" align="right">
        <template #default="{ row }">{{ Number(row.planAmount).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="已核销" width="140" align="right">
        <template #default="{ row }">{{ Number(row.settledAmount).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="待核销" width="140" align="right">
        <template #default="{ row }">{{ Number(row.unsettledAmount).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="PaymentPlanStatus[row.status]?.type">
            {{ PaymentPlanStatus[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="逾期" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.overdue" type="danger" size="small">逾期 {{ row.overdueDays }} 天</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'payment:plan'" link size="small" @click="openPlanForm(row)">编辑</el-button>
          <el-popconfirm
            v-if="canDelete(row)"
            :title="`确认删除第 ${row.periodNo} 期？`"
            @confirm="onDelete(row)"
          >
            <template #reference>
              <el-button v-perm="'payment:plan'" link size="small">
                <span class="text-danger">删除</span>
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="genDialog" title="批量生成回款计划" width="500px">
      <el-form :model="genForm" label-width="100px">
        <el-form-item label="首期日期">
          <el-date-picker v-model="genForm.firstPlanDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期数">
          <el-input-number v-model="genForm.periods" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="频率">
          <el-radio-group v-model="genForm.frequency">
            <el-radio value="MONTHLY">月</el-radio>
            <el-radio value="QUARTERLY">季</el-radio>
            <el-radio value="ONCE">一次性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="覆盖现有">
          <el-switch v-model="genForm.overwrite" />
          <span class="text-muted ml-1">开启后会先删除该合同已有计划</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <PaymentPlanFormDialog
      v-model:visible="planFormDialog"
      :contract-id="contractId"
      :plan="planEditing"
      :next-period-no="nextPeriodNo"
      @saved="onPlanSaved"
    />
    <PaymentPlanBatchDialog v-model:visible="batchDialog" :contract-id="contractId" @saved="onPlanSaved" />

    <PaymentRecordDrawer
      v-model:visible="recordDrawer"
      :default-contract-id="contractId"
      @saved="onRecordSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Money, Plus, Refresh, Tickets } from '@element-plus/icons-vue'
import {
  paymentPlanApi,
  PaymentPlanStatus,
  type PaymentPlanSummaryVO,
  type PaymentPlanVO
} from '@/api/payment'
import { formatDate } from '@/utils/format'
import EmptyHint from '@/components/EmptyHint.vue'
import PaymentRecordDrawer from '@/views/payment/components/PaymentRecordDrawer.vue'
import PaymentPlanFormDialog from '@/views/payment/components/PaymentPlanFormDialog.vue'
import PaymentPlanBatchDialog from '@/views/payment/components/PaymentPlanBatchDialog.vue'

const props = defineProps<{ contractId: string }>()

const rows = ref<PaymentPlanVO[]>([])
const loading = ref(false)

const genDialog = ref(false)
const generating = ref(false)
const genForm = reactive({
  firstPlanDate: new Date().toISOString().slice(0, 10),
  periods: 12,
  frequency: 'MONTHLY' as 'MONTHLY' | 'QUARTERLY' | 'ONCE',
  overwrite: false
})

const planSummary = ref<PaymentPlanSummaryVO | null>(null)
const planFormDialog = ref(false)
const planEditing = ref<PaymentPlanVO | null>(null)
const batchDialog = ref(false)

const nextPeriodNo = computed(() =>
  rows.value.reduce((max, r) => Math.max(max, Number(r.periodNo || 0)), 0) + 1
)

const recordDrawer = ref(false)

// 请求 token：仅最后一次结果生效，避免快速切合同时旧请求覆盖新数据
let reqToken = 0

async function loadData() {
  if (!props.contractId) return
  const my = ++reqToken
  loading.value = true
  try {
    const data = await paymentPlanApi.byContract(props.contractId)
    if (my === reqToken) rows.value = data
  } finally {
    if (my === reqToken) loading.value = false
  }
}

async function loadSummary() {
  if (!props.contractId) return
  try {
    planSummary.value = await paymentPlanApi.summary(props.contractId)
  } catch {
    planSummary.value = null
  }
}

function loadAll() {
  loadData()
  loadSummary()
}

watch(() => props.contractId, (id) => { if (id) loadAll() }, { immediate: true })

async function onGenerate() {
  generating.value = true
  try {
    const ids = await paymentPlanApi.generate({
      contractId: props.contractId,
      firstPlanDate: genForm.firstPlanDate,
      periods: genForm.periods,
      frequency: genForm.frequency,
      overwrite: genForm.overwrite
    })
    ElMessage.success(`已生成 ${ids.length} 期`)
    genDialog.value = false
    loadAll()
  } finally {
    generating.value = false
  }
}

function openPlanForm(row: PaymentPlanVO | null) {
  planEditing.value = row
  planFormDialog.value = true
}

function onPlanSaved() {
  planFormDialog.value = false
  batchDialog.value = false
  loadAll()
}

function canDelete(row: PaymentPlanVO) {
  return Number(row.settledAmount) === 0
}

async function onDelete(row: PaymentPlanVO) {
  await paymentPlanApi.remove(row.id)
  ElMessage.success('已删除')
  loadAll()
}

function onRecordSaved() {
  recordDrawer.value = false
  loadAll()
}

function money(v: number | undefined) {
  return Number(v || 0).toLocaleString()
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.text-muted {
  color: #909399;
}
.mb-2 {
  margin-bottom: 12px;
}
.ml-1 {
  margin-left: 6px;
}
</style>
