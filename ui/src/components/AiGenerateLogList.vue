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
          <el-table-column label="时间">
            <template #default="{ row }">
              <span class="text-sm text-gray-600">
                {{ formatTime(row.metadata.creationTimestamp) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="主题">
            <template #default="{ row }">
              <el-tooltip
                      :content="row.spec.topic || '-'"
                      placement="top"
                      :disabled="!row.spec.topic"
              >
                <span class="log-cell-ellipsis text-sm font-medium text-gray-900">
                  {{ row.spec.topic || '-' }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="模型">
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
                      :content="getCategoryName(row.spec.categoryName)"
                      placement="top"
                      :disabled="!row.spec.categoryName"
              >
                <span class="log-cell-ellipsis text-sm text-gray-600">
                  {{ getCategoryName(row.spec.categoryName) }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="请求/成功/失败">
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
          <el-table-column label="耗时">
            <template #default="{ row }">
              <span class="text-sm text-gray-600">{{ formatDuration(row.spec.durationMs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="自动发布">
            <template #default="{ row }">
              <el-tag :type="row.spec.autoPublish ? 'success' : 'info'" size="small">
                {{ row.spec.autoPublish ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="{ row }">
              <VStatusDot
                      v-if="isDeleting(row)"
                      animate
                      state="warning"
                      text="删除中"
              />
              <el-tag
                      v-else
                      :type="getStatusType(row.spec.status)"
                      size="small"
              >
                {{ getStatusLabel(row.spec.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="(cmd: string) => handleRowAction(cmd, row)">
                <button class="flex items-center justify-center rounded p-1 hover:bg-gray-100" type="button">
                  <IconMore class="h-4 w-4 text-gray-600"/>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span class="text-red-500">删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <!-- 日志详情弹窗 -->
    <VModal
            v-model:visible="showDetailModal"
            title="AI 生成日志详情"
            :width="640"
    >
      <div v-if="detailLog" class="form-modal-body">
        <div class="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div class="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span class="text-gray-500">时间：</span>
              <span class="text-gray-900">{{ formatTime(detailLog.metadata.creationTimestamp) }}</span>
            </div>
            <div>
              <span class="text-gray-500">状态：</span>
              <el-tag :type="getStatusType(detailLog.spec.status)" size="small">
                {{ getStatusLabel(detailLog.spec.status) }}
              </el-tag>
            </div>
            <div>
              <span class="text-gray-500">主题：</span>
              <span class="text-gray-900">{{ detailLog.spec.topic || '-' }}</span>
            </div>
            <div>
              <span class="text-gray-500">模型：</span>
              <span class="text-gray-900">{{ detailLog.spec.modelName || '-' }}</span>
            </div>
            <div>
              <span class="text-gray-500">分类：</span>
              <span class="text-gray-900">{{ getCategoryName(detailLog.spec.categoryName) }}</span>
            </div>
            <div>
              <span class="text-gray-500">耗时：</span>
              <span class="text-gray-900">{{ formatDuration(detailLog.spec.durationMs) }}</span>
            </div>
            <div>
              <span class="text-gray-500">请求/成功/失败：</span>
              <span class="text-gray-900">
                {{ detailLog.spec.requestCount }} /
                <span class="text-green-600">{{ detailLog.spec.successCount }}</span> /
                <span class="text-red-600">{{ detailLog.spec.failedCount }}</span>
              </span>
            </div>
            <div>
              <span class="text-gray-500">自动发布：</span>
              <span class="text-gray-900">{{ detailLog.spec.autoPublish ? '是' : '否' }}</span>
            </div>
          </div>
        </div>

        <div v-if="detailLog.spec.errorMessage" class="mb-3 rounded-lg border border-red-200 bg-red-50 p-3">
          <div class="mb-1 text-xs text-red-500">错误信息</div>
          <div class="text-sm text-red-700">{{ detailLog.spec.errorMessage }}</div>
        </div>

        <div class="rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div class="mb-2 text-xs text-gray-500">AI 生成的源数据</div>
          <div v-if="parsedGeneratedData.length > 0" class="space-y-2">
            <div
                    v-for="(item, index) in parsedGeneratedData"
                    :key="index"
                    class="rounded-md border border-gray-200 bg-white p-2.5"
            >
              <div class="text-sm text-gray-900">{{ item.content || '-' }}</div>
              <div class="mt-1 flex items-center gap-3 text-xs text-gray-500">
                <span>作者：{{ item.author || '匿名' }}</span>
                <span>来源：{{ item.source || '未知' }}</span>
              </div>
            </div>
          </div>
          <div v-else class="py-4 text-center text-sm text-gray-400">
            无源数据（该日志可能生成失败或未保存源数据）
          </div>
        </div>
      </div>
      <template #footer>

      </template>
    </VModal>
  </VCard>
</template>

<script setup lang="ts">
import {
  Dialog,
  IconMore,
  IconRefreshLine,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VModal,
  VPagination,
  VStatusDot,
} from '@halo-dev/components'
import {MagicStick} from '@element-plus/icons-vue'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {axiosInstance} from '@halo-dev/api-client'
import {categoryCoreApiClient} from '@/api'
import {useToast} from '@/composables/useToast'

const toast = useToast()

interface AiGenerateLog {
  apiVersion: string
  kind: string
  metadata: {
    name: string
    creationTimestamp: string
    deletionTimestamp?: string
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
    generatedData?: string
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
const categories = ref<any[]>([])

// 详情弹窗状态
const showDetailModal = ref(false)
const detailLog = ref<AiGenerateLog | null>(null)

// 删除轮询
let deletionRefetchTimer: ReturnType<typeof setInterval> | null = null
// 生成轮询
let generatePollTimer: ReturnType<typeof setInterval> | null = null
// 跳过下一次加载态（用于无感刷新时切换页码）
let skipLoadingFetch = false

const isDeleting = (log: AiGenerateLog): boolean => {
  return !!log.metadata?.deletionTimestamp
}

const hasDeletingLogs = computed(() => logs.value.some((log) => isDeleting(log)))

const hasRunningLogs = computed(() =>
  logs.value.some((log) => log.spec.status === 'RUNNING'),
)

const parsedGeneratedData = computed<Array<{ content?: string; author?: string; source?: string }>>(() => {
  if (!detailLog.value?.spec?.generatedData) return []
  try {
    return JSON.parse(detailLog.value.spec.generatedData)
  } catch {
    return []
  }
})

const getCategoryName = (categoryName?: string): string => {
  if (!categoryName) return '-'
  const category = categories.value.find((c) => c.metadata.name === categoryName)
  return category?.spec?.name || categoryName
}

const initCategories = async () => {
  try {
    const {data} = await categoryCoreApiClient.category.listCategory({page: 1, size: 100})
    categories.value = data.items || []
  } catch (e) {
    console.error('获取分类列表失败', e)
  }
}

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
    toast.error('加载AI生成日志失败')
  } finally {
    loading.value = false
  }
}

const fetchLogsSilently = async () => {
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
    console.error('静默刷新日志列表失败', e)
  }
}

const startDeletionRefetch = () => {
  if (deletionRefetchTimer) {
    clearInterval(deletionRefetchTimer)
  }
  deletionRefetchTimer = setInterval(async () => {
    if (hasDeletingLogs.value) {
      await fetchLogsSilently()
    } else {
      stopDeletionRefetch()
      await fetchLogsSilently()
    }
  }, 1000)
}

const stopDeletionRefetch = () => {
  if (deletionRefetchTimer) {
    clearInterval(deletionRefetchTimer)
    deletionRefetchTimer = null
  }
}

/* AI 生成轮询：静默刷新日志直到没有 RUNNING 状态的记录 */
const startGeneratePolling = () => {
  if (generatePollTimer) {
    clearInterval(generatePollTimer)
  }
  let pollCount = 0
  const maxPollCount = 150 /* 最多轮询 5 分钟（每 2 秒一次） */
  generatePollTimer = setInterval(async () => {
    pollCount++
    if (pollCount > maxPollCount) {
      stopGeneratePolling()
      return
    }
    await fetchLogsSilently()
    if (!hasRunningLogs.value) {
      stopGeneratePolling()
    }
  }, 2000)
}

const stopGeneratePolling = () => {
  if (generatePollTimer) {
    clearInterval(generatePollTimer)
    generatePollTimer = null
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
    toast.success('AI生成任务已触发，请稍后查看日志')
    /* 无感刷新：回到第一页静默拉取，然后轮询直到生成完成 */
    if (page.value !== 1) {
      skipLoadingFetch = true
      page.value = 1
    } else {
      await fetchLogsSilently()
    }
    startGeneratePolling()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '触发AI生成失败'
    toast.error(msg)
  } finally {
    triggering.value = false
  }
}

const handleDetail = (log: AiGenerateLog) => {
  detailLog.value = log
  showDetailModal.value = true
}

const handleRowAction = (command: string, log: AiGenerateLog) => {
  if (command === 'detail') {
    handleDetail(log)
  } else if (command === 'delete') {
    handleDelete(log)
  }
}

const handleDelete = (log: AiGenerateLog) => {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除该AI生成日志吗？该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await axiosInstance.delete(
          `/apis/console.api.hitokotohub.puresky.top/v1alpha1/ai-generate-logs/${log.metadata.name}`,
        )
        toast.success('删除成功')
        await fetchLogsSilently()
        startDeletionRefetch()
      } catch (e: any) {
        const msg = e?.response?.data?.message || '删除失败'
        toast.error(msg)
      }
    },
  })
}

const handleDetailDelete = () => {
  if (!detailLog.value) return
  const log = detailLog.value
  showDetailModal.value = false
  handleDelete(log)
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

watch(page, () => {
  if (skipLoadingFetch) {
    skipLoadingFetch = false
    fetchLogsSilently()
  } else {
    fetchLogs()
  }
})
watch(size, () => {
  page.value = 1
  fetchLogs()
})

onMounted(() => {
  initCategories()
  fetchLogs()
})

onUnmounted(() => {
  stopDeletionRefetch()
  stopGeneratePolling()
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
