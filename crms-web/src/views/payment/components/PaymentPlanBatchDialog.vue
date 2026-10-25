<template>
  <el-dialog
    :model-value="visible"
    title="批量录入回款计划"
    width="min(820px, 94vw)"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-alert
      v-if="summary"
      :type="diff === 0 ? 'success' : 'error'"
      :closable="false"
      show-icon
    >
      合同金额 <strong>{{ money(summary.contractAmount) }}</strong>，本次合计
      <strong>{{ money(total) }}</strong>，差额 <strong>{{ money(diff) }}</strong>
      <template v-if="diff !== 0">：同一合同回款计划合计必须恰好等于合同金额，差额为 0 才能保存。</template>
    </el-alert>

    <div class="toolbar mt-2">
      <el-button :icon="Plus" @click="addRow">新增一期</el-button>
      <el-checkbox v-model="overwrite">覆盖已有计划（存在核销记录时不可覆盖）</el-checkbox>
    </div>

    <el-table :data="rows" border size="small" max-height="360">
      <el-table-column label="期数" width="110">
        <template #default="{ row }">
          <el-input-number v-model="row.periodNo" :min="1" :controls="false" style="width: 80px" />
        </template>
      </el-table-column>
      <el-table-column label="计划日期" width="180">
        <template #default="{ row }">
          <el-date-picker v-model="row.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="计划金额" min-width="180">
        <template #default="{ row }">
          <el-input-number v-model="row.planAmount" :min="0" :precision="2" :step="1000" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="提醒提前（天）" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.remindDays" :min="0" :max="365" :controls="false" style="width: 90px" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button link size="small" type="danger" @click="rows.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <span class="mr-auto text-muted">共 {{ rows.length }} 期</span>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!canSave" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  paymentPlanApi,
  type BatchPlanItem,
  type PaymentPlanSummaryVO
} from '@/api/payment'

const props = defineProps<{ visible: boolean; contractId: string }>()
const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

const summary = ref<PaymentPlanSummaryVO | null>(null)
const rows = ref<BatchPlanItem[]>([])
const overwrite = ref(true)
const saving = ref(false)

const total = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(r.planAmount || 0), 0)
)
const diff = computed(() =>
  summary.value ? round2(Number(summary.value.contractAmount) - total.value) : 0
)
const canSave = computed(
  () =>
    rows.value.length > 0 &&
    !!summary.value &&
    diff.value === 0 &&
    rows.value.every((r) => Number(r.planAmount) > 0)
)

watch(
  () => props.visible,
  async (v) => {
    if (!v || !props.contractId) return
    rows.value = []
    summary.value = null
    overwrite.value = true
    try {
      const [s, plans] = await Promise.all([
        paymentPlanApi.summary(props.contractId),
        paymentPlanApi.byContract(props.contractId)
      ])
      summary.value = s
      rows.value = plans.map((p) => ({
        periodNo: p.periodNo,
        planDate: p.planDate,
        planAmount: Number(p.planAmount),
        remindDays: p.remindDays ?? 7
      }))
    } catch {
      /* ignore */
    }
  }
)

function addRow() {
  const next = rows.value.reduce((max, r) => Math.max(max, Number(r.periodNo || 0)), 0) + 1
  rows.value.push({
    periodNo: next,
    planDate: new Date().toISOString().slice(0, 10),
    planAmount: 0,
    remindDays: 7
  })
}

async function onSave() {
  saving.value = true
  try {
    await paymentPlanApi.batch({
      contractId: props.contractId,
      plans: rows.value,
      overwrite: overwrite.value
    })
    ElMessage.success('已保存回款计划')
    emit('saved')
  } finally {
    saving.value = false
  }
}

function round2(v: number) {
  return Math.round(v * 100) / 100
}

function money(v: number | undefined) {
  return Number(v || 0).toLocaleString()
}
</script>

<style scoped>
.mt-2 {
  margin-top: 12px;
}
.mb-2 {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.text-muted {
  color: #909399;
}
.mr-auto {
  margin-right: auto;
}
</style>
