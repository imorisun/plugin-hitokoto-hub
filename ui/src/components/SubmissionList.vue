<template>
  <VCard :body-class="['!p-0']">
    <template #header>
      <div class="sentence-card-header">
        <div class="min-w-0">
          <div class="text-base font-semibold text-gray-900">访客提交审核</div>
          <div class="mt-0.5 text-xs text-gray-500">
            待审核 {{ pendingCount }} 条 · 共 {{ total }} 条记录
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <el-select
                  v-model="statusFilter"
                  placeholder="状态筛选"
                  clearable
                  size="small"
                  style="width: 140px"
                  @change="handleStatusChange"
          >
            <el-option label="全部" value=""/>
            <el-option label="待审核" value="PENDING"/>
            <el-option label="已通过" value="APPROVED"/>
            <el-option label="已拒绝" value="REJECTED"/>
          </el-select>
          <button
                  v-tooltip="'刷新'"
                  class="group rounded p-1 hover:bg-gray-200"
                  type="button"
                  @click="fetchSubmissions"
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
              v-else-if="submissions.length === 0"
              title="暂无提交"
              message="访客提交的句子会在此显示，您可以审核后决定是否加入句子库"
      />
      <el-scrollbar v-else max-height="600px">
        <VEntityContainer>
          <VEntity v-for="submission in submissions" :key="submission.metadata.name">
            <template #start>
              <VEntityField max-width="560px">
                <template #title>
                  <span
                          :title="submission.spec.content"
                          class="submission-content block truncate whitespace-normal wrap-break-word text-sm font-medium text-gray-900"
                          @click="handleDetail(submission)"
                  >
                    {{ submission.spec.content }}
                  </span>
                </template>
                <template #description>
                  <div class="flex flex-wrap items-center gap-1 text-gray-500">
                    <VTag>作者：{{ submission.spec.author || '匿名' }}</VTag>
                    <VTag>来源：{{ submission.spec.source || '未知' }}</VTag>
                    <VTag>分类：{{ getCategoryName(submission.spec.categoryName) }}</VTag>
                    <VTag v-if="submission.spec.submitterName">
                      提交者：{{ submission.spec.submitterName }}
                    </VTag>
                    <VTag>IP：{{ submission.spec.submitterIp || '-' }}</VTag>
                  </div>
                </template>
              </VEntityField>
            </template>
            <template #end>
              <VEntityField>
                <template #description>
                  <span class="text-xs text-gray-500">
                    {{ formatTime(submission.metadata.creationTimestamp) }}
                  </span>
                </template>
              </VEntityField>
              <VEntityField>
                <template #description>
                  <VStatusDot
                          v-if="isDeleting(submission)"
                          animate
                          state="warning"
                          text="删除中"
                  />
                  <el-tag
                          v-else
                          :type="getStatusType(submission.spec.status)"
                          size="small"
                  >
                    {{ getStatusLabel(submission.spec.status) }}
                  </el-tag>
                </template>
              </VEntityField>
              <VEntityField v-if="submission.spec.reviewedBy">
                <template #description>
                  <span class="text-xs text-gray-500">
                    {{ submission.spec.reviewedBy }}
                  </span>
                </template>
              </VEntityField>
            </template>
            <template #dropdownItems>
              <VDropdownItem @click="handleDetail(submission)">查看详情</VDropdownItem>
              <template v-if="canManage && submission.spec.status === 'PENDING'">
                <VDropdownItem @click="handleApprove(submission)">通过并加入句子库</VDropdownItem>
                <VDropdownItem type="danger" @click="handleReject(submission)">拒绝</VDropdownItem>
              </template>
              <VDropdownItem v-if="canManage" type="danger" @click="handleDelete(submission)">删除</VDropdownItem>
            </template>
          </VEntity>
        </VEntityContainer>
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

    <!-- 通过审核弹窗：可编辑句子内容 -->
    <VModal
            v-model:visible="showApproveModal"
            title="通过提交（可编辑内容）"
            :width="560"
    >
      <div class="form-modal-body">
        <div class="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div class="mb-1 text-xs text-gray-500">原始提交内容（供参考）</div>
          <div class="text-sm text-gray-700">{{ reviewingSubmission?.spec.content }}</div>
          <div class="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-gray-600">
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              作者：{{ reviewingSubmission?.spec.author || '匿名' }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              来源：{{ reviewingSubmission?.spec.source || '未知' }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              分类：{{ getCategoryName(reviewingSubmission?.spec.categoryName) }}
            </span>
          </div>
        </div>
        <FormKit
                v-model="approveForm.content"
                type="textarea"
                label="句子内容"
                validation="required"
                validation-message="请输入句子内容"
                placeholder="可编辑后加入句子库"
                :rows="4"
        />
        <FormKit
                v-model="approveForm.categoryName"
                type="select"
                label="分类"
                validation="required"
                validation-message="请选择分类"
                placeholder="请选择分类"
                :options="categorySelectOptions"
        />
        <FormKit
                v-model="approveForm.author"
                type="text"
                label="作者"
                placeholder="留空则记为匿名"
        />
        <FormKit
                v-model="approveForm.source"
                type="text"
                label="来源"
                placeholder="如书名、电影、歌曲等"
        />
        <FormKit
                v-model="approveForm.reviewNote"
                type="textarea"
                label="审核备注（可选）"
                placeholder="仅在详情中展示，不对外公开"
                :rows="2"
        />
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="showApproveModal = false">取消</VButton>
          <VButton
                  type="secondary"
                  :loading="reviewing"
                  @click="handleSubmitApprove"
          >
            确认通过
          </VButton>
        </div>
      </template>
    </VModal>

    <!-- 拒绝审核弹窗：可填写拒绝理由 -->
    <VModal
            v-model:visible="showRejectModal"
            title="拒绝提交"
            :width="520"
    >
      <div class="form-modal-body">
        <div class="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div class="text-sm font-medium text-gray-900">
            {{ reviewingSubmission?.spec.content }}
          </div>
          <div class="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-gray-600">
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              作者：{{ reviewingSubmission?.spec.author || '匿名' }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              来源：{{ reviewingSubmission?.spec.source || '未知' }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              分类：{{ getCategoryName(reviewingSubmission?.spec.categoryName) }}
            </span>
          </div>
        </div>
        <FormKit
                v-model="rejectForm.rejectionReason"
                type="textarea"
                label="拒绝理由（可选）"
                placeholder="会记录为审核备注"
                :rows="3"
        />
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="showRejectModal = false">取消</VButton>
          <VButton
                  type="danger"
                  :loading="reviewing"
                  @click="handleSubmitReject"
          >
            确认拒绝
          </VButton>
        </div>
      </template>
    </VModal>

    <!-- 提交详情弹窗 -->
    <VModal
            v-model:visible="showDetailModal"
            title="提交详情"
            :width="560"
    >
      <div v-if="detailSubmission" class="form-modal-body">
        <div class="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div class="text-sm font-medium text-gray-900">{{ detailSubmission.spec.content }}</div>
          <div class="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-gray-600">
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              作者：{{ detailSubmission.spec.author || '匿名' }}
            </span>
            <span v-if="detailSubmission.spec.source"
                  class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              来源：{{ detailSubmission.spec.source }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              分类：{{ getCategoryName(detailSubmission.spec.categoryName) }}
            </span>
            <span class="inline-flex items-center rounded-full bg-white px-2.5 py-0.5">
              <el-tag :type="getStatusType(detailSubmission.spec.status)" size="small" style="border:none;font-weight:500;">
                {{ getStatusLabel(detailSubmission.spec.status) }}
              </el-tag>
            </span>
          </div>
        </div>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-item-label">提交者</span>
            <span class="detail-item-value">
              {{ detailSubmission.spec.submitterName || '-' }}
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-item-label">IP 地址</span>
            <span class="detail-item-value">
              {{ detailSubmission.spec.submitterIp || '-' }}
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-item-label">提交时间</span>
            <span class="detail-item-value">
              {{ formatTime(detailSubmission.metadata.creationTimestamp) }}
            </span>
          </div>
          <template v-if="detailSubmission.spec.status !== 'PENDING'">
            <div class="detail-item">
              <span class="detail-item-label">审核人</span>
              <span class="detail-item-value">
                {{ detailSubmission.spec.reviewedBy || '-' }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-item-label">审核时间</span>
              <span class="detail-item-value">
                {{ formatTime(detailSubmission.spec.reviewedAt) }}
              </span>
            </div>
          </template>
        </div>
        <div
                v-if="detailSubmission.spec.reviewNote"
                class="detail-note-box"
        >
          <div class="detail-note-label">
            {{ detailSubmission.spec.status === 'APPROVED' ? '审核备注' : '拒绝理由' }}
          </div>
          <div class="detail-note-text">{{ detailSubmission.spec.reviewNote }}</div>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton
                  v-if="canManage && detailSubmission?.spec.status === 'PENDING'"
                  type="danger"
                  @click="handleDetailReject"
          >
            拒绝
          </VButton>
          <VButton
                  v-if="canManage && detailSubmission?.spec.status === 'PENDING'"
                  type="secondary"
                  @click="handleDetailApprove"
          >
            通过
          </VButton>
          <VButton @click="showDetailModal = false">关闭</VButton>
        </div>
      </template>
    </VModal>
  </VCard>
</template>

<script setup lang="ts">
import {
  Dialog,
  IconRefreshLine,
  Toast,
  VButton,
  VCard,
  VDropdownItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VPagination,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import {utils} from '@halo-dev/ui-shared'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {axiosInstance} from '@halo-dev/api-client'
import {categoryCoreApiClient} from '@/api'

interface SentenceSubmission {
  apiVersion: string
  kind: string
  metadata: {
    name: string
    creationTimestamp: string
    deletionTimestamp?: string
  }
  spec: {
    content: string
    author?: string
    source?: string
    categoryName: string
    submitterName?: string
    submitterIp?: string
    status: 'PENDING' | 'APPROVED' | 'REJECTED'
    reviewedBy?: string
    reviewNote?: string
    reviewedAt?: string
    sentenceName?: string
  }
}

interface SubmissionList {
  page: number
  size: number
  total: number
  items: SentenceSubmission[]
}

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const submissions = ref<SentenceSubmission[]>([])
const statusFilter = ref('')
const categories = ref<any[]>([])
const canManage = computed(() => utils.permission.has(['plugin:hitokoto-hub:manage']))

// 删除轮询
let deletionRefetchTimer: ReturnType<typeof setInterval> | null = null

const isDeleting = (submission: SentenceSubmission): boolean => {
  return !!submission.metadata?.deletionTimestamp
}

const hasDeletingSubmissions = computed(() =>
  submissions.value.some((s) => isDeleting(s)),
)

// 通过审核弹窗状态
const showApproveModal = ref(false)
const reviewingSubmission = ref<SentenceSubmission | null>(null)
const reviewing = ref(false)
const approveForm = ref({
  content: '',
  categoryName: '',
  author: '',
  source: '',
  reviewNote: '',
})

// 拒绝审核弹窗状态
const showRejectModal = ref(false)
const rejectForm = ref({
  rejectionReason: '',
})

// 详情弹窗状态
const showDetailModal = ref(false)
const detailSubmission = ref<SentenceSubmission | null>(null)

const pendingCount = computed(
  () => submissions.value.filter((s) => s.spec.status === 'PENDING').length,
)

const categorySelectOptions = computed(() =>
  categories.value.map((c) => ({
    label: c.spec.name,
    value: c.metadata.name,
  })),
)

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

const fetchSubmissions = async () => {
  loading.value = true
  try {
    const params: any = {page: page.value, size: size.value}
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const {data} = await axiosInstance.get<SubmissionList>(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence-submissions',
      {params},
    )
    submissions.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    console.error('获取访客提交列表失败', e)
    Toast.error('加载访客提交列表失败')
  } finally {
    loading.value = false
  }
}

const fetchSubmissionsSilently = async () => {
  try {
    const params: any = {page: page.value, size: size.value}
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const {data} = await axiosInstance.get<SubmissionList>(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence-submissions',
      {params},
    )
    submissions.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    console.error('Silent fetch failed', e)
  }
}

const startDeletionRefetch = () => {
  if (deletionRefetchTimer) {
    clearInterval(deletionRefetchTimer)
  }
  deletionRefetchTimer = setInterval(async () => {
    if (hasDeletingSubmissions.value) {
      await fetchSubmissionsSilently()
    } else {
      stopDeletionRefetch()
      await fetchSubmissionsSilently()
    }
  }, 1000)
}

const stopDeletionRefetch = () => {
  if (deletionRefetchTimer) {
    clearInterval(deletionRefetchTimer)
    deletionRefetchTimer = null
  }
}

const handleStatusChange = () => {
  page.value = 1
  fetchSubmissions()
}

const handleApprove = (submission: SentenceSubmission) => {
  reviewingSubmission.value = submission
  approveForm.value = {
    content: submission.spec.content || '',
    categoryName: submission.spec.categoryName || '',
    author: submission.spec.author || '',
    source: submission.spec.source || '',
    reviewNote: '',
  }
  showApproveModal.value = true
}

const handleReject = (submission: SentenceSubmission) => {
  reviewingSubmission.value = submission
  rejectForm.value = {
    rejectionReason: '',
  }
  showRejectModal.value = true
}

const handleDetail = (submission: SentenceSubmission) => {
  detailSubmission.value = submission
  showDetailModal.value = true
}

const handleDetailApprove = () => {
  if (!detailSubmission.value) return
  const submission = detailSubmission.value
  showDetailModal.value = false
  handleApprove(submission)
}

const handleDetailReject = () => {
  if (!detailSubmission.value) return
  const submission = detailSubmission.value
  showDetailModal.value = false
  handleReject(submission)
}

const handleSubmitApprove = async () => {
  if (!reviewingSubmission.value) return
  if (!approveForm.value.content.trim()) {
    Toast.error('请输入句子内容')
    return
  }
  if (!approveForm.value.categoryName) {
    Toast.error('请选择分类')
    return
  }
  reviewing.value = true
  try {
    const name = reviewingSubmission.value.metadata.name
    const body: Record<string, string> = {
      content: approveForm.value.content.trim(),
      categoryName: approveForm.value.categoryName,
    }
    if (approveForm.value.author.trim()) {
      body.author = approveForm.value.author.trim()
    }
    if (approveForm.value.source.trim()) {
      body.source = approveForm.value.source.trim()
    }
    if (approveForm.value.reviewNote.trim()) {
      body.reviewNote = approveForm.value.reviewNote.trim()
    }
    await axiosInstance.post(
      `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence-submissions/${name}/approve`,
      body,
    )
    Toast.success('已通过并加入句子库')
    showApproveModal.value = false
    await fetchSubmissions()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '审核操作失败'
    Toast.error(msg)
  } finally {
    reviewing.value = false
  }
}

const handleSubmitReject = async () => {
  if (!reviewingSubmission.value) return
  reviewing.value = true
  try {
    const name = reviewingSubmission.value.metadata.name
    const body: Record<string, string> = {}
    if (rejectForm.value.rejectionReason.trim()) {
      body.rejectionReason = rejectForm.value.rejectionReason.trim()
    }
    await axiosInstance.post(
      `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence-submissions/${name}/reject`,
      body,
    )
    Toast.success('已拒绝该提交')
    showRejectModal.value = false
    await fetchSubmissions()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '审核操作失败'
    Toast.error(msg)
  } finally {
    reviewing.value = false
  }
}

const handleDelete = (submission: SentenceSubmission) => {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除该提交记录吗？该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await axiosInstance.delete(
          `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence-submissions/${submission.metadata.name}`,
        )
        Toast.success('删除成功')
        await fetchSubmissionsSilently()
        startDeletionRefetch()
      } catch (e: any) {
        const msg = e?.response?.data?.message || '删除失败'
        Toast.error(msg)
      }
    },
  })
}

const formatTime = (timestamp?: string): string => {
  if (!timestamp) return '-'
  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return timestamp
  }
}

const getStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'danger'
    default:
      return 'info'
  }
}

const getStatusLabel = (status: string): string => {
  switch (status) {
    case 'PENDING':
      return '待审核'
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已拒绝'
    default:
      return status
  }
}

watch(page, () => fetchSubmissions())
watch(size, () => {
  page.value = 1
  fetchSubmissions()
})

onMounted(() => {
  initCategories()
  fetchSubmissions()
})

onUnmounted(() => {
  stopDeletionRefetch()
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

:deep(.el-tag) {
  border: none;
  font-weight: 500;
}

.form-modal-body {
  padding: 4px 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.submission-content {
  cursor: pointer;
  transition: color 0.2s;
}

.submission-content:hover {
  color: #fb7185;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
  padding: 0 4px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-item-label {
  font-size: 12px;
  color: #9ca3af;
  letter-spacing: 0.02em;
}

.detail-item-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}

.detail-note-box {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

.detail-note-label {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
  margin-bottom: 4px;
}

.detail-note-text {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.6;
  word-break: break-all;
}
</style>
