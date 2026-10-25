<template>
  <el-dialog
    :model-value="visible"
    :title="plan ? '编辑回款计划' : '新增回款计划'"
    width="520px"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-alert
      v-if="summary"
      :type="diffAfter === 0 ? 'success' : 'error'"
      :closable="false"
      show-icon
      class="mb-2"
    >
      合同金额 {{ money(summary.contractAmount) }}，已排合计 {{ money(summary.plannedAmount) }}，
      本次后差额 <strong>{{ money(diffAfter) }}</strong>
      <template v-if="diffAfter !== 0">（后端要求合计必须恰好等于合同金额，否则保存会被拒绝）</template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="期数" prop="periodNo">
        <el-input-number v-model="form.periodNo" :min="1" :disabled="!!plan" style="width: 160px" />
      </el-form-item>
      <el-form-item label="计划日期" prop="planDate">
        <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="计划金额" prop="planAmount">
        <el-input-number v-model="form.planAmount" :min="0.01" :precision="2" :step="1000" style="width: 100%" />
      </el-form-item>
      <el-form-item label="提醒提前">
        <el-input-number v-model="form.remindDays" :min="0" :max="365" />
        <span class="text-muted ml-1">天</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  paymentPlanApi,
  type PaymentPlanSummaryVO,
  type PaymentPlanVO
} from '@/api/payment'

const props = defineProps<{
  visible: boolean
  contractId: string
  plan?: PaymentPlanVO | null
  /** 新增时预填的下一期期数。 */
  nextPeriodNo?: number
}>()

const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const summary = ref<PaymentPlanSummaryVO | null>(null)

const form = reactive({
  periodNo: 1,
  planDate: new Date().toISOString().slice(0, 10),
  planAmount: 0,
  remindDays: 7
})

const rules: FormRules = {
  periodNo: [{ required: true, message: '请填写期数', trigger: 'blur' }],
  planDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }],
  planAmount: [{ required: true, message: '请填写计划金额', trigger: 'blur' }]
}

const diffAfter = computed(() => {
  if (!summary.value) return 0
  const base =
    Number(summary.value.plannedAmount) -
    (props.plan ? Number(props.plan.planAmount) : 0) +
    Number(form.planAmount || 0)
  return round2(Number(summary.value.contractAmount) - base)
})

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    Object.assign(form, {
      periodNo: props.plan?.periodNo ?? props.nextPeriodNo ?? 1,
      planDate: props.plan?.planDate ?? new Date().toISOString().slice(0, 10),
      planAmount: props.plan ? Number(props.plan.planAmount) : 0,
      remindDays: props.plan?.remindDays ?? 7
    })
    summary.value = null
    if (props.contractId) {
      try {
        summary.value = await paymentPlanApi.summary(props.contractId)
      } catch {
        /* ignore */
      }
    }
  }
)

async function onSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (props.plan) {
      await paymentPlanApi.update(props.plan.id, {
        planDate: form.planDate,
        planAmount: form.planAmount,
        remindDays: form.remindDays,
        version: props.plan.version
      })
      ElMessage.success('已更新')
    } else {
      await paymentPlanApi.create({
        contractId: props.contractId,
        periodNo: form.periodNo,
        planDate: form.planDate,
        planAmount: form.planAmount,
        remindDays: form.remindDays
      })
      ElMessage.success('已新增')
    }
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
.mb-2 {
  margin-bottom: 12px;
}
.text-muted {
  color: #909399;
}
.ml-1 {
  margin-left: 6px;
}
</style>
