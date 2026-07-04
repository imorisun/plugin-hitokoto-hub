<template>
  <VCard :body-class="['!p-0']">
    <template #header>
      <div class="sentence-card-header">
        <div class="min-w-0">
          <div class="text-base font-semibold text-gray-900">AI 生成日志</div>
          <div class="mt-0.5 text-xs text-gray-500">
            共 {{ total }} 条记录
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <VButton
                  size="sm"
                  type="primary"
                  :loading="triggering"
                  @click="handleTriggerGenerate"
          >
            <template #icon>
              <el-icon><MagicStick/></el-icon>
            </template>
            立即生成
          </VButton>
          <el-select
                  v-model="statusFilter"
                  placeholder="状态筛选"
                  clearable
                  size="small"
                  style="width: 140px"
                  @change="handleStatusChange"
          >
            <el-option label="全部" value=""/>
            <el-option label="进行中" value="RUNNING"/>
            <el-option label="成功" value="SUCCESS"/>
            <el-option label="部分成功" value="PARTIAL_SUCCESS"/>
            <el-option label="失败" value="FAILED"/>
          </el-select>
          <button
                  v-tooltip="'刷新'"
                  class="group rounded p-1 hover:bg-gray-200"
                  type="button"
                  @click="fetchLogs"
          >
            <IconRefreshLine class="h-4 w-4 text-gray-600 group-hover:text-gray-900"/>
          </button>
        </div>
      </div>
    </template>

    <div>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <VLoading/>
      </div>
      <VEmpty
              v-else-if="logs.length === 0"
              title="暂无日志"
              message="AI 生成句子后会在此显示记录"
      />
      <el-scrollbar v-else max-height="600px">
        <el-table
                :data="logs"
                style="width: 100%"
                :stripe="true"
                table-layout="auto"
        >
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">
              <span class="text-sm text-gray-600">
                {{ formatTime(row.metadata.creationTimestamp) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="主题" min-width="200">
            <template #default="{ row }">
              <el-tooltip
                      :content="row.spec.topic || '-'"
                      placement="top"
                      :disabled="!row.spec.topic"
              >
                <span
                        class="log-cell-ellipsis text-sm font-medium text-gray-900"
                >
                  {{ row.spec.topic || '-' }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="80">
            <template #default="{ row }">
              <el-tooltip
                      :content="row.spec.modelName || '-'"
                      placement="top"
                      :disabled="!row.spec.modelName"
              >
                <span class="log-cell-ellipsis text-sm text-gray-600">
                  {{ row.spec.modelName || '-' }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="分类" min-width="100">
            <template #default="{ row }">
              <el-tooltip
                      :content="row.spec.categoryName || '-'"
                      placement="top"
                      :disabled="!row.spec.categoryName"
              >
                <span class="log-cell-ellipsis text-sm text-gray-600">
                  {{ row.spec.categoryName || '-' }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="请求/成功/失败" min-width="140">
            <template #default="{ row }">
              <div class="flex items-center gap-1.5 text-sm">
                <span class="text-gray-500">{{ row.spec.requestCount }}</span>
                <span class="text-gray-300">/</span>
                <span class="font-medium text-green-600">{{ row.spec.successCount }}</span>
                <span class="text-gray-300">/</span>
                <span class="font-medium text-red-600">{{ row.spec.failedCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="耗时" min-width="90">
            <template #default="{ row }">
              <span class="text-sm text-gray-600">{{ formatDuration(row.spec.durationMs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="自动发布" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.spec.autoPublish ? 'success' : 'info'" size="small">
                {{ row.spec.autoPublish ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.spec.status)" size="small">
                {{ getStatusLabel(row.spec.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误信息" min-width="200">
            <template #default="{ row }">
              <el-tooltip
                      v-if="row.spec.errorMessage"
                      :content="row.spec.errorMessage"
                      placement="top"
              >
                <span class="block truncate text-sm text-red-500" style="max-width: 200px">
                  {{ row.spec.errorMessage }}
                </span>
              </el-tooltip>
              <span v-else class="text-sm text-gray-400">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-scrollbar>
    </div>
    <div class="sentence-list-pagination">
      <VPagination
              v-model:page="page"
              v-model:size="size"
              page-label="页"
              size-label="条 / 页"
              :total-label="`共 ${total} 项数据`"
              :total="total"
              :size-options="[20, 30, 50, 100]"
      />
    </div>
  </VCard>
</template>

<script setup lang="ts">
import {IconRefreshLine, Toast, VButton, VCard, VEmpty, VLoading, VPagination} from '@halo-dev/components'
import {MagicStick} from '@element-plus/icons-vue'
import {onMounted, ref, watch} from 'vue'
import {axiosInstance} from '@halo-dev/api-client'

interface AiGenerateLog {
  apiVersion: string
  kind: string
  metadata: {
    name: string
    creationTimestamp: string
  }
  spec: {
    modelName?: string
    topic?: string
    requestCount: number
    successCount: number
    failedCount: number
    categoryName?: string
    autoPublish: boolean
    status: 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED'
    errorMessage?: string
    durationMs: number
  }
}

interface AiGenerateLogList {
  page: number
  size: number
  total: number
  items: AiGenerateLog[]
}

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const logs = ref<AiGenerateLog[]>([])
const statusFilter = ref('')
const triggering = ref(false)

const fetchLogs = async () => {
  loading.value = true
  try {
    const params: any = {page: page.value, size: size.value}
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const {data} = await axiosInstance.get<AiGenerateLogList>(
            '/apis/console.api.hitokotohub.puresky.top/v1alpha1/ai-generate-logs',
            {params}
    )
    logs.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    console.error('获取AI生成日志失败', e)
    Toast.error('加载AI生成日志失败')
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  page.value = 1
  fetchLogs()
}

const handleTriggerGenerate = async () => {
  triggering.value = true
  try {
    await axiosInstance.post(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/ai-generate-logs/-/trigger'
    )
    Toast.success('AI生成任务已触发，请稍后查看日志')
    // 短暂延迟后刷新列表，便于看到 RUNNING 记录
    setTimeout(() => {
      page.value = 1
      fetchLogs()
    }, 1000)
  } catch (e: any) {
    const msg = e?.response?.data?.message || '触发AI生成失败'
    Toast.error(msg)
  } finally {
    triggering.value = false
  }
}

const formatTime = (timestamp: string): string => {
  if (!timestamp) return '-'
  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return timestamp
  }
}

const formatDuration = (ms: number): string => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const getStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'PARTIAL_SUCCESS':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
}

const getStatusLabel = (status: string): string => {
  switch (status) {
    case 'RUNNING':
      return '进行中'
    case 'SUCCESS':
      return '成功'
    case 'PARTIAL_SUCCESS':
      return '部分成功'
    case 'FAILED':
      return '失败'
    default:
      return status
  }
}

watch(page, () => fetchLogs())
watch(size, () => {
  page.value = 1
  fetchLogs()
})

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.sentence-card-header {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 16px;
  padding: 12px 16px;
}

.sentence-list-pagination {
  padding: 12px 16px;
  border-top: 1px solid #eaecf0;
}

:deep(.el-table) {
  border: none !important;
  border-radius: 0;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  padding: 0.75rem 1rem;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #f3f4f6;
  padding: 0.75rem 1rem;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background-color: #f9fafb;
}

:deep(.el-tag) {
  border: none;
  font-weight: 500;
}

.log-cell-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}
</style>
