<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="640px"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-alert type="info" :closable="false" show-icon>
      {{ hint }}
    </el-alert>

    <div class="mt-2">
      <el-button link type="primary" :icon="Download" @click="onDownload">下载导入模板</el-button>
    </div>

    <el-upload
      drag
      class="mt-2"
      :show-file-list="false"
      :http-request="onUpload"
      :before-upload="beforeUpload"
      accept=".xls,.xlsx"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        将 Excel 文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">支持 .xls / .xlsx，单次最多 2000 行数据。</div>
      </template>
    </el-upload>

    <div v-if="uploading" class="mt-2">
      <el-progress :percentage="100" :indeterminate="true" />
      正在上传与解析...
    </div>

    <div v-if="result" class="mt-2">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="总条数">{{ result.total }}</el-descriptions-item>
        <el-descriptions-item label="成功">
          <el-tag type="success">{{ result.success }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败">
          <el-tag type="danger">{{ result.failed }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-table v-if="result.errors.length" :data="result.errors" class="mt-2" border size="small">
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column prop="message" label="错误" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import type { ImportResultVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  title: string
  hint: string
  importFn: (file: File) => Promise<ImportResultVO>
  downloadTemplateFn?: () => Promise<Blob>
  templateFilename?: string
}>()

const emit = defineEmits<{ 'update:visible': [boolean]; finished: [] }>()

const uploading = ref(false)
const result = ref<ImportResultVO | null>(null)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      result.value = null
    }
  }
)

function beforeUpload(file: File) {
  if (!/\.xls[x]?$/i.test(file.name)) {
    ElMessage.error('仅支持 .xls / .xlsx 文件')
    return false
  }
  return true
}

async function onUpload(opt: UploadRequestOptions) {
  uploading.value = true
  result.value = null
  try {
    result.value = await props.importFn(opt.file as File)
    if (result.value.failed === 0) {
      ElMessage.success(`已导入 ${result.value.success} 条记录`)
    } else {
      ElMessage.warning(`导入完成：成功 ${result.value.success}、失败 ${result.value.failed}`)
    }
    emit('finished')
  } finally {
    uploading.value = false
  }
}

async function onDownload() {
  if (!props.downloadTemplateFn) {
    ElMessage.warning('未配置模板下载')
    return
  }
  try {
    const blob = await props.downloadTemplateFn()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = props.templateFilename || 'import-template.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('模板下载失败')
  }
}
</script>

<style scoped>
.mt-2 {
  margin-top: 12px;
}
</style>
