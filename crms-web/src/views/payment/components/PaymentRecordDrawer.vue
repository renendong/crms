<template>
  <el-drawer
    :model-value="visible"
    title="登记实际回款"
    size="500px"
    direction="rtl"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="合同" prop="contractId">
        <el-select
          v-model="form.contractId"
          filterable
          remote
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
      <el-form-item label="到账日期" prop="arrivalDate">
        <el-date-picker
          v-model="form.arrivalDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="期数" prop="periodNo">
        <el-select
          v-model="form.periodNo"
          placeholder="请选择回款计划期数"
          style="width: 100%"
          :disabled="!form.contractId"
        >
          <el-option
            v-for="p in plans"
            :key="p.id"
            :label="`第 ${p.periodNo} 期 · ${p.planDate} · 待核销 ${Number(p.unsettledAmount).toLocaleString()}`"
            :value="p.periodNo"
          />
        </el-select>
        <div v-if="form.contractId && !plans.length" class="hint">
          该合同暂无未结清的回款计划，请先在「回款计划」中录入
        </div>
      </el-form-item>
      <el-form-item label="金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0" :precision="2" :step="1000" style="width: 100%" />
      </el-form-item>
      <el-form-item label="付款方">
        <el-input v-model="form.payer" maxlength="100" />
      </el-form-item>
      <el-form-item label="凭证号">
        <el-input v-model="form.voucherNo" maxlength="255" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存并核销</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { contractApi, type ContractVO } from '@/api/contract'
import { paymentPlanApi, paymentRecordApi, type CreateRecordDTO, type PaymentPlanVO } from '@/api/payment'

const props = defineProps<{ visible: boolean; defaultContractId?: string }>()
const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const contractLoading = ref(false)
const contractOptions = ref<ContractVO[]>([])
const plans = ref<PaymentPlanVO[]>([])

const form = reactive<RecordForm>(emptyForm())

const rules: FormRules = {
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
  periodNo: [{ required: true, message: '请选择回款计划期数', trigger: 'change' }],
  arrivalDate: [{ required: true, message: '请选择到账日期', trigger: 'change' }],
  amount: [
    { required: true, message: '请填写金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '金额必须大于 0', trigger: 'blur' }
  ]
}

watch(
  () => props.visible,
  async (v) => {
    if (v) {
      Object.assign(form, emptyForm())
      contractOptions.value = []
      plans.value = []
      if (props.defaultContractId) {
        try {
          const c = await contractApi.detail(props.defaultContractId)
          contractOptions.value = [c]
          form.contractId = c.id
          await onContractChange(c.id)
        } catch {
          /* ignore */
        }
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

async function onContractChange(id: string) {
  form.periodNo = undefined
  if (!id) {
    plans.value = []
    return
  }
  try {
    plans.value = (await paymentPlanApi.byContract(id)).filter((p) => p.status !== 'SETTLED')
  } catch {
    plans.value = []
  }
}

async function onSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await paymentRecordApi.create({ ...form, periodNo: form.periodNo as number })
    ElMessage.success('已登记并执行核销')
    emit('saved')
  } finally {
    saving.value = false
  }
}

type RecordForm = Omit<CreateRecordDTO, 'periodNo'> & { periodNo?: number }

function emptyForm(): RecordForm {
  return {
    contractId: '',
    periodNo: undefined,
    arrivalDate: new Date().toISOString().slice(0, 10),
    amount: 0,
    payer: '',
    voucherNo: '',
    remark: ''
  }
}
</script>

<style scoped>
.hint {
  margin-top: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
}
</style>
