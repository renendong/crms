<template>
  <el-drawer
    :model-value="visible"
    :title="record ? '编辑收入明细' : '新增收入明细'"
    size="560px"
    direction="rtl"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="合同" prop="contractId">
        <el-select
          v-model="form.contractId"
          filterable
          remote
          :disabled="!!record"
          :remote-method="searchContract"
          :loading="contractLoading"
          placeholder="输入合同名称/编号"
          style="width: 100%"
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
      <el-form-item label="收入归属期间" prop="periodMonth">
        <el-date-picker
          v-model="form.periodMonth"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择归属月份"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="确认日期" prop="recognizeDate">
        <el-date-picker v-model="form.recognizeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="收入类型">
        <el-select v-model="form.incomeType" style="width: 100%">
          <el-option
            v-for="(meta, key) in IncomeTypeLabel"
            :key="key"
            :label="meta.label"
            :value="key"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="含税金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="1000" style="width: 100%" />
      </el-form-item>
      <el-form-item label="税率">
        <el-input-number v-model="form.taxRate" :min="0" :max="1" :precision="4" :step="0.01" style="width: 180px" />
        <span class="text-muted ml-1">0~1，如 0.13 表示 13%</span>
      </el-form-item>
      <el-form-item label="成本金额">
        <el-input-number v-model="form.costAmount" :min="0" :precision="2" :step="100" style="width: 100%" />
      </el-form-item>
      <el-form-item label="单据号">
        <el-input v-model="form.voucherNo" maxlength="255" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
    </el-form>

    <el-descriptions :column="3" border size="small" class="mt-2">
      <el-descriptions-item label="税额">{{ money(derived.taxAmount) }}</el-descriptions-item>
      <el-descriptions-item label="不含税">{{ money(derived.netAmount) }}</el-descriptions-item>
      <el-descriptions-item label="毛利">{{ money(derived.grossProfit) }}</el-descriptions-item>
    </el-descriptions>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { contractApi, type ContractVO } from '@/api/contract'
import { IncomeTypeLabel, revenueApi, type RevenueRecordVO } from '@/api/revenue'

const props = defineProps<{ visible: boolean; record?: RevenueRecordVO | null }>()
const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const contractLoading = ref(false)
const contractOptions = ref<ContractVO[]>([])

const form = reactive({
  contractId: '',
  periodMonth: '',
  recognizeDate: new Date().toISOString().slice(0, 10),
  incomeType: 'SERVICE',
  amount: 0,
  taxRate: 0,
  costAmount: 0,
  voucherNo: '',
  remark: ''
})

const rules: FormRules = {
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
  periodMonth: [{ required: true, message: '请选择收入归属期间', trigger: 'change' }],
  recognizeDate: [{ required: true, message: '请选择确认日期', trigger: 'change' }],
  amount: [{ required: true, message: '请填写含税金额', trigger: 'blur' }]
}

const derived = computed(() => {
  const amount = Number(form.amount || 0)
  const tax = round2(amount * Number(form.taxRate || 0))
  const net = round2(amount - tax)
  return { taxAmount: tax, netAmount: net, grossProfit: round2(net - Number(form.costAmount || 0)) }
})

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    const r = props.record
    Object.assign(form, {
      contractId: r?.contractId ?? '',
      periodMonth: r?.periodMonth ?? '',
      recognizeDate: r?.recognizeDate ?? new Date().toISOString().slice(0, 10),
      incomeType: r?.incomeType ?? 'SERVICE',
      amount: r ? Number(r.amount) : 0,
      taxRate: r ? Number(r.taxRate) : 0,
      costAmount: r ? Number(r.costAmount) : 0,
      voucherNo: r?.voucherNo ?? '',
      remark: r?.remark ?? ''
    })
    contractOptions.value = []
    if (r) {
      try {
        const c = await contractApi.detail(r.contractId)
        contractOptions.value = [c]
      } catch {
        /* ignore */
      }
    }
  }
)

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

function onContractChange(id: string) {
  if (!id) return
  const picked = contractOptions.value.find((c) => c.id === id)
  if (picked && !form.amount) form.amount = Number(picked.amount || 0)
}

async function onSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      contractId: form.contractId,
      periodMonth: form.periodMonth,
      recognizeDate: form.recognizeDate,
      incomeType: form.incomeType,
      amount: form.amount,
      taxRate: form.taxRate,
      costAmount: form.costAmount,
      voucherNo: form.voucherNo || undefined,
      remark: form.remark || undefined
    }
    if (props.record) {
      await revenueApi.update(props.record.id, { ...payload, version: props.record.version })
      ElMessage.success('已更新')
    } else {
      await revenueApi.create(payload)
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
.mt-2 {
  margin-top: 12px;
}
.text-muted {
  color: #909399;
}
.ml-1 {
  margin-left: 6px;
}
</style>
