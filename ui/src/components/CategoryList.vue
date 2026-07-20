<template>
  <div class="category-list p-4">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-3">
        <div class="text-sm text-gray-500">
          共 {{ total }} 个分类
        </div>
        <div
                class="group cursor-pointer rounded p-1 hover:bg-gray-200"
                v-tooltip="'刷新'"
                @click="refreshData"
        >
          <IconRefreshLine class="h-4 w-4 text-gray-600 group-hover:text-gray-900" />
        </div>
      </div>
      <VButton size="sm" type="secondary" @click="handleCreate">
        <template #icon>
          <IconAddCircle class="h-full w-full" />
        </template>
        新建分类
      </VButton>
    </div>

    <!-- Loading -->
    <VLoading v-if="loading" />

    <!-- Empty -->
    <VEmpty
            v-else-if="regularCategories.length === 0"
            title="暂无分类"
            message="点击「新建分类」创建你的第一个分类"
    />

    <!-- Category List -->
    <template v-else>
      <VEntityContainer>
        <VEntity
                v-for="category in regularCategories"
                :key="category.metadata.name"
        >
          <template #start>
            <VEntityField :title="category.spec.name">
              <template #description>
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600 whitespace-nowrap">
                    描述：{{ category.spec.description || '暂无描述' }}
                  </span>
                </div>
              </template>
            </VEntityField>
          </template>
          <template #end>
            <VEntityField>
              <template #description>
                <div class="flex items-center gap-1.5">
                  <span
                          v-if="isDeleting(category)"
                          class="inline-block h-2 w-2 rounded-full bg-red-500 animate-pulse"
                          v-tooltip="'删除中'"
                  />
                  <VTag>
                    {{ category.sentenceCount ?? 0 }} 条句子
                  </VTag>
                </div>
              </template>
            </VEntityField>
            <VEntityField>
              <template #description>
                <VDropdown>
                  <VButton size="sm" type="default">操作</VButton>
                  <template #popper>
                    <VDropdownItem @click="handleEdit(category)">编辑</VDropdownItem>
                    <VDropdownItem
                            v-if="!isDeleting(category)"
                            type="danger"
                            @click="handleDelete(category)"
                    >
                      删除
                    </VDropdownItem>
                  </template>
                </VDropdown>
              </template>
            </VEntityField>
          </template>
        </VEntity>

        <!-- 未分类：系统内置分类 -->
        <VEntity v-if="uncategorizedCategory">
          <template #start>
            <VEntityField title="未分类">
              <template #description>
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600 whitespace-nowrap">
                    描述：系统内置分类，用于存放未指定分类或分类不存在的句子
                  </span>
                </div>
              </template>
            </VEntityField>
          </template>
          <template #end>
            <VEntityField>
              <template #description>
                <div class="flex items-center gap-1.5">
                  <VTag>
                    {{ uncategorizedCategory.sentenceCount ?? 0 }} 条句子
                  </VTag>
                </div>
              </template>
            </VEntityField>
            <VEntityField>
              <template #description>
                <VDropdown>
                  <VButton size="sm" type="default">操作</VButton>
                  <template #popper>
                    <VDropdownItem
                            type="danger"
                            @click="handleClearUncategorized"
                    >
                      清空句子
                    </VDropdownItem>
                  </template>
                </VDropdown>
              </template>
            </VEntityField>
          </template>
        </VEntity>
      </VEntityContainer>

      <!-- Pagination -->
      <div class="mt-4 flex justify-center">
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
    </template>

    <!-- Create/Edit Modal -->
    <VModal
            v-model:visible="showFormModal"
            :title="isEditing ? '编辑分类' : '新建分类'"
            :width="520"
    >
      <div class="halo-form">
        <div class="halo-form-item">
          <label class="halo-form-label">
            分类名称 <span class="text-red-500">*</span>
          </label>
          <input
                  v-model="formData.specName"
                  type="text"
                  class="halo-input"
                  placeholder="请输入分类名称"
                  aria-required="true"
          />
        </div>
        <div class="halo-form-item">
          <label class="halo-form-label">分类描述</label>
          <textarea
                  v-model="formData.description"
                  rows="3"
                  class="halo-input halo-textarea"
                  placeholder="请输入分类描述（可选）"
          />
        </div>
      </div>
      <template #footer>
        <VSpace>
          <VButton size="sm" @click="showFormModal = false">取消</VButton>
          <VButton size="sm" type="secondary" :loading="saving" @click="handleSave">
            保存
          </VButton>
        </VSpace>
      </template>
    </VModal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import type {CategoryWithCount} from '@/api'
import {categoryCoreApiClient, sentenceCoreApiClient} from '@/api'
import type {Category} from '@/api/generated'
import {
  Dialog,
  IconAddCircle,
  IconRefreshLine,
  VButton,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VPagination,
  VSpace,
  VTag,
} from '@halo-dev/components'
import {useToast} from '@/composables/useToast'

const toast = useToast()

const loading = ref(false)
const categories = ref<CategoryWithCount[]>([])
const saving = ref(false)

// 存储正在删除的分类名称（本地标记，避免重复调用 API）
const deletingNames = ref<Set<string>>(new Set())

// 轮询相关
let pollingTimer: ReturnType<typeof setInterval> | null = null
const POLLING_INTERVAL = 3000 // 3 秒轮询一次

const page = ref(1)
const size = ref(20)
const total = ref(0)

const showFormModal = ref(false)
const isEditing = ref(false)
const formCategory = ref<Category | null>(null)

const formData = ref({
  specName: '',
  description: '',
})

const UNCATEGORIZED_NAME = 'uncategorized'

const regularCategories = computed(() =>
  categories.value.filter((c) => c.metadata.name !== UNCATEGORIZED_NAME),
)

const uncategorizedCategory = computed(() =>
        categories.value.find((c) => c.metadata.name === UNCATEGORIZED_NAME) ?? null,
)

// 通过 deletionTimestamp 判断是否正在删除
const isDeleting = (category: Category): boolean => {
  return !!category.metadata?.deletionTimestamp
}

// 检查列表中是否有正在删除的项
const hasDeletingItems = (): boolean => {
  return categories.value.some(c => isDeleting(c))
}

// 开始轮询
const startPolling = () => {
  if (pollingTimer) return
  pollingTimer = setInterval(async () => {
    await fetchCategoriesSilently()
  }, POLLING_INTERVAL)
}

// 停止轮询
const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 静默刷新（不显示 loading）
async function fetchCategoriesSilently() {
  try {
    const { data } = await categoryCoreApiClient.listCategoriesWithCounts({
      page: page.value,
      size: size.value,
    })

    const newItems = data.items || []

    // 检查之前标记删除的是否已经彻底消失
    const existingNames = new Set(newItems.map(c => c.metadata.name))
    for (const name of deletingNames.value) {
      if (!existingNames.has(name)) {
        // 已经从后端彻底清除，移除本地标记
        deletingNames.value.delete(name)
      }
    }

    // 无感更新数据
    categories.value = newItems
    total.value = data.total || 0

    // 如果还有正在删除的项，继续轮询；否则停止
    if (hasDeletingItems()) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (e) {
    // 静默失败，不影响用户
    console.error('静默刷新分类列表失败', e)
  }
}

// 正常刷新（显示 loading）
async function fetchCategories() {
  loading.value = true
  try {
    const { data } = await categoryCoreApiClient.listCategoriesWithCounts({
      page: page.value,
      size: size.value,
    })
    categories.value = data.items || []
    total.value = data.total || 0

    // 清理已经不存在的删除记录
    const existingNames = new Set((data.items || []).map(c => c.metadata.name))
    for (const name of deletingNames.value) {
      if (!existingNames.has(name)) {
        deletingNames.value.delete(name)
      }
    }

    // 如果有正在删除的项，开始轮询
    if (hasDeletingItems()) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (e) {
    console.error('获取分类列表失败', e)
    toast.error('加载分类列表失败')
  } finally {
    loading.value = false
  }
}

function refreshData() {
  fetchCategories()
}

watch(page, () => {
  fetchCategories()
})

watch(size, () => {
  page.value = 1
  fetchCategories()
})

function handleCreate() {
  isEditing.value = false
  formCategory.value = null
  formData.value = { specName: '', description: '' }
  showFormModal.value = true
}

function handleEdit(category: Category) {
  isEditing.value = true
  formCategory.value = category
  formData.value = {
    specName: category.spec.name || '',
    description: category.spec.description || '',
  }
  showFormModal.value = true
}

async function handleSave() {
  if (!formData.value.specName.trim()) {
    toast.warning('请输入分类名称')
    return
  }
  saving.value = true
  try {
    if (isEditing.value && formCategory.value) {
      // 重新获取最新数据，防止多次修改导致版本冲突
      const { data: latestCategory } = await categoryCoreApiClient.category.getCategory({
        name: formCategory.value.metadata.name,
      })
      const updated: Category = {
        ...latestCategory,
        spec: {
          ...latestCategory.spec,
          name: formData.value.specName.trim(),
          description: formData.value.description.trim() || undefined,
        },
      }
      await categoryCoreApiClient.category.updateCategory({
        name: formCategory.value.metadata.name,
        category: updated,
      })
      toast.success('更新分类成功')
    } else {
      const newCategory: Category = {
        apiVersion: 'hitokotohub.puresky.top/v1alpha1',
        kind: 'Category',
        metadata: {
          name: '',
          generateName: 'category-',
        },
        spec: {
          name: formData.value.specName.trim(),
          description: formData.value.description.trim() || undefined,
        },
      }
      await categoryCoreApiClient.category.createCategory({
        category: newCategory,
      })
      toast.success('创建分类成功')
    }
    showFormModal.value = false
    await fetchCategories()
  } catch (e) {
    console.error('保存分类失败', e)
    toast.error(isEditing.value ? '更新分类失败' : '创建分类失败')
  } finally {
    saving.value = false
  }
}

function handleDelete(category: Category) {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除分类「${category.spec.name}」吗？该分类下的句子将归入「未分类」，该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      const name = category.metadata.name
      try {
        // 调用删除 API，后端会设置 deletionTimestamp
        await categoryCoreApiClient.category.deleteCategory({ name })
        toast.success('删除成功')

        // 立即刷新一次，让列表显示 deletionTimestamp 状态
        await fetchCategoriesSilently()

        // 如果有删除中的项，启动轮询
        if (hasDeletingItems()) {
          startPolling()
        }
      } catch (e) {
        console.error('删除分类失败', e)
        toast.error('删除分类失败')
      }
    },
  })
}

async function handleClearUncategorized() {
  Dialog.warning({
    title: '清空未分类句子',
    description: '确定要清空所有未分类的句子吗？这些句子将被永久删除，该操作不可撤销。',
    confirmType: 'danger',
    confirmText: '清空',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        const { data: deletedCount } = await sentenceCoreApiClient.clearUncategorizedSentences()
        toast.success(`已清空 ${deletedCount} 条未分类句子`)
        await fetchCategories()
      } catch (e) {
        console.error('清空未分类句子失败', e)
        toast.error('清空未分类句子失败')
      }
    },
  })
}

onMounted(() => {
  fetchCategories()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.category-list {
  min-height: 20px;
}

.halo-form {
  padding: 4px 0;
}

.halo-form-item {
  margin-bottom: 16px;
}

.halo-form-item:last-child {
  margin-bottom: 0;
}

.halo-form-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  line-height: 1.5;
}

.halo-input {
  display: block;
  width: 100%;
  padding: 7px 12px;
  font-size: 13px;
  line-height: 1.5;
  color: #1e293b;
  background-color: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  box-sizing: border-box;
  font-family: inherit;
}

.halo-input::placeholder {
  color: #9ca3af;
}

.halo-input:hover {
  border-color: #9ca3af;
}

.halo-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.halo-textarea {
  resize: vertical;
  min-height: 72px;
}
</style>
