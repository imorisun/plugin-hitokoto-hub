<template>
  <div>
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="sentence-card-header">
          <div class="min-w-0">
            <div class="text-base font-semibold text-gray-900">句子管理</div>
            <div class="mt-0.5 text-xs text-gray-500">
              {{ activeCategoryName }} · 共 {{ total }} 条句子
            </div>
          </div>
          <div v-if="canManage" class="flex shrink-0 flex-row items-center gap-2">
            <VButton size="sm" @click="handleBatchImport('json')">
              <template #icon>
                <IconUpload class="h-full w-full"/>
              </template>
              JSON 导入
            </VButton>
            <VButton size="sm" @click="handleBatchImport('excel')">
              <template #icon>
                <IconUpload class="h-full w-full"/>
              </template>
              Excel 导入
            </VButton>
            <VButton size="sm" type="secondary" @click="handleCreate">
              <template #icon>
                <IconAddCircle class="h-full w-full"/>
              </template>
              新建句子
            </VButton>
          </div>
        </div>
      </template>

      <div class="sentence-workbench">
        <aside class="sentence-category-pane">
          <div class="sentence-category-pane__header">
            <div>
              <div class="text-sm font-semibold text-gray-900">分类</div>
              <div class="mt-0.5 text-xs text-gray-500">{{ categories.length }} 个分类</div>
            </div>
            <div class="flex items-center gap-1">
              <button
                      v-if="canManage"
                      v-tooltip="'新建分类'"
                      class="group rounded p-1 hover:bg-gray-200"
                      type="button"
                      @click="handleCreateCategory"
              >
                <IconAddCircle class="h-4 w-4 text-gray-600 group-hover:text-gray-900"/>
              </button>
              <button
                      v-tooltip="'刷新分类'"
                      class="group rounded p-1 hover:bg-gray-200"
                      type="button"
                      @click="initCategories"
              >
                <IconRefreshLine class="h-4 w-4 text-gray-600 group-hover:text-gray-900"/>
              </button>
            </div>
          </div>
          <!--          分类区块-->
          <el-scrollbar max-height="606px">
            <div class="category-nav">
              <button
                      :class="{ 'category-nav__item--active': !selectedCategory }"
                      class="category-nav__item"
                      type="button"
                      @click="handleCategorySelect(undefined)"
              >
              <span class="category-nav__text">
                <span class="category-nav__name">全部分类</span>
                <span class="category-nav__count">{{ totalCategorySentenceCount }} 条句子</span>
              </span>
              </button>
              <div
                      v-for="category in categories"
                      :key="category.metadata.name"
                      :class="{ 'category-nav__item--active': selectedCategory === category.metadata.name }"
                      class="category-nav__item category-nav__item--editable"
                      role="button"
                      tabindex="0"
                      @click="!isDeletingCategory(category) && handleCategorySelect(category.metadata.name)"
                      @keydown.enter="
                !isDeletingCategory(category) && handleCategorySelect(category.metadata.name)
              "
              >
              <span class="category-nav__text">
                <span class="category-nav__name">{{ category.spec.name }}</span>
                <span class="category-nav__count flex items-center gap-1 flex-row">
                  <VStatusDot v-if="isDeletingCategory(category)" v-bind="{state:'warning'}"/>
                  {{
                    isDeletingCategory(category)
                            ? '删除中'
                            : `${category.status?.sentenceCount ?? 0} 条句子`
                  }}</span>
              </span>
                <span v-if="!isDeletingCategory(category) && canManage"
                      class="category-nav__actions">
                <button
                        v-tooltip="'编辑分类'"
                        class="category-nav__action"
                        type="button"
                        @click.stop="handleEditCategory(category)"
                >
                  <EditPen class="h-3.5 w-3.5"/>
                </button>
                <button
                        v-tooltip="'删除分类'"
                        class="category-nav__action category-nav__action--danger"
                        type="button"
                        @click.stop="handleDeleteCategory(category)"
                >
                  <Delete class="h-3.5 w-3.5"/>
                </button>
              </span>
              </div>
            </div>
          </el-scrollbar>

        </aside>

        <section class="sentence-list-pane">
          <div class="sentence-list-toolbar">
            <div class="flex min-w-0 flex-1 items-center">
              <SearchInput v-model="keyword" @keyup.enter="handleSearch"/>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <FilterCleanButton v-if="hasFilters" @click="handleClearFilters"/>
              <FilterDropdown
                      v-model="selectedSort"
                      label="排序"
                      :items="[
                  { label: '默认' },
                  { label: '较近创建', value: 'metadata.creationTimestamp,desc' },
                  { label: '较早创建', value: 'metadata.creationTimestamp,asc' },
                  { label: '最多浏览', value: 'status.viewCount,desc' },
                  { label: '最少浏览', value: 'status.viewCount,asc' },
                  { label: '最多点赞', value: 'status.likeCount,desc' },
                  { label: '最少点赞', value: 'status.likeCount,asc' },
                ]"
              />
              <button
                      v-tooltip="'刷新'"
                      class="group rounded p-1 hover:bg-gray-200"
                      type="button"
                      @click="refreshData"
              >
                <IconRefreshLine class="h-4 w-4 text-gray-600 group-hover:text-gray-900"/>
              </button>
            </div>
          </div>

          <div>
            <div v-if="loading" class="flex items-center justify-center py-20">
              <VLoading/>
            </div>
            <VEmpty
                    v-else-if="sentences.length === 0"
                    title="暂无句子"
                    message="点击「新建句子」创建你的第一条句子"
            />
            <!--句子列表-->
            <el-scrollbar v-else max-height="550px">
              <VEntityContainer>
                <VEntity v-for="sentence in sentences" :key="sentence.metadata.name">
                  <template #start>
                    <VEntityField max-width="620px">
                      <template #title>
                    <span :title="sentence.spec.content"
                          class="block truncate  whitespace-normal wrap-break-word text-sm font-medium text-gray-900">
                      {{ sentence.spec.content }}
                    </span>
                      </template>
                      <template #description>
                        <div class="flex items-center gap-1 text-gray-500">
                          <VTag>作者：{{ sentence.spec.author || '匿名' }}</VTag>
                          <VTag>来源：{{ sentence.spec.source || '未知' }}</VTag>
                          <VTag>分类：{{ getCategoryName(sentence.spec.categoryName) }}</VTag>
                        </div>
                      </template>
                    </VEntityField>
                  </template>
                  <template #end>
                    <VEntityField>
                      <template #description>
                        <VStatusDot v-if="isDeleting(sentence)" animate state="error"
                                    text="删除中"/>
                        <VStatusDot
                                v-else
                                :state="sentence.status?.published ? 'success' : 'default'"
                                :text="sentence.status?.published ? '已发布' : '未发布'"
                        />
                      </template>
                    </VEntityField>
                    <VEntityField>
                      <template #description>
                        <div class="flex items-center gap-1 text-gray-500">
                          <el-icon :size="14">
                            <IconLike/>
                          </el-icon>
                          <span :title="'点赞'" class="text-sm">{{
                              sentence.status?.likeCount ?? 0
                            }}</span>
                        </div>
                      </template>
                    </VEntityField>
                    <VEntityField>
                      <template #description>
                        <div class="flex items-center gap-1 text-gray-500">
                          <el-icon :size="14">
                            <View/>
                          </el-icon>
                          <span class="text-sm">{{ sentence.status?.viewCount ?? 0 }}</span>
                        </div>
                      </template>
                    </VEntityField>
                  </template>
                  <template v-if="canManage" #dropdownItems>
                    <VDropdownItem @click="handleEdit(sentence)">编辑</VDropdownItem>
                    <VDropdownItem
                            v-if="!isDeleting(sentence)"
                            type="danger"
                            @click="handleDelete(sentence)"
                    >
                      删除
                    </VDropdownItem>
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
        </section>
      </div>
    </VCard>
    <!-- 创建/编辑句子弹窗 -->
    <VModal
            v-model:visible="showFormModal"
            :title="isEditing ? '编辑句子' : '新建句子'"
            :width="600"
    >
      <div class="form-modal-body">
        <FormKit
                v-model="formData.content"
                type="textarea"
                label="句子内容"
                validation="required"
                validation-message="请输入句子内容"
                placeholder="请输入句子内容"
                :rows="4"
        />
        <FormKit
                v-model="formData.categoryName"
                type="select"
                label="分类"
                validation="required"
                validation-message="请选择分类"
                placeholder="请选择分类"
                :options="categorySelectOptions"
        />
        <FormKit
                v-model="formData.author"
                type="text"
                label="作者"
                placeholder="请输入作者（默认为匿名）"
        />
        <FormKit
                v-model="formData.source"
                type="text"
                label="来源"
                placeholder="请输入来源（默认为未知）"
        />
        <FormKit
                v-if="isEditing"
                v-model="formData.published"
                type="checkbox"
                label="发布状态"
                help="勾选后公开对外可见，未勾选则仅管理员可见"
        >
          已发布
        </FormKit>
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="showFormModal = false">取消</VButton>
          <VButton type="secondary" :loading="saving" @click="handleSave">
            {{ isEditing ? '保存修改' : '创建' }}
          </VButton>
        </div>
      </template>
    </VModal>

    <VModal
            v-model:visible="showCategoryFormModal"
            :title="isEditingCategory ? '编辑分类' : '新建分类'"
            :width="520"
    >
      <div class="form-modal-body">
        <FormKit
                v-model="categoryFormData.specName"
                type="text"
                label="分类名称"
                validation="required"
                validation-message="请输入分类名称"
                placeholder="请输入分类名称"
        />
        <FormKit
                v-model="categoryFormData.description"
                type="textarea"
                label="分类描述"
                placeholder="请输入分类描述（可选）"
                :rows="3"
        />
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="showCategoryFormModal = false">取消</VButton>
          <VButton type="secondary" :loading="savingCategory" @click="handleSaveCategory">
            保存
          </VButton>
        </div>
      </template>
    </VModal>

    <!-- 批量导入弹窗 -->
    <VModal v-model:visible="showBatchImportModal" title="批量导入句子" :width="900">
      <div class="form-modal-body">
        <FormKit
                v-model="batchImportForm.categoryName"
                type="select"
                label="目标分类"
                validation="required"
                validation-message="请选择分类"
                placeholder="请选择分类"
                :options="categorySelectOptions"
        />

        <div class="mt-4 flex items-center gap-2">
          <button
                  :class="{ 'batch-mode-button--active': batchImportMode === 'json' }"
                  class="batch-mode-button"
                  type="button"
                  @click="batchImportMode = 'json'"
          >
            JSON
          </button>
          <button
                  :class="{ 'batch-mode-button--active': batchImportMode === 'excel' }"
                  class="batch-mode-button"
                  type="button"
                  @click="batchImportMode = 'excel'"
          >
            Excel
          </button>
        </div>

        <template v-if="batchImportMode === 'json'">
          <FormKit
                  v-model="batchImportForm.jsonText"
                  type="textarea"
                  label="粘贴 JSON 数据"
                  validation="required"
                  validation-message="请粘贴 JSON 数据"
                  placeholder='[
  {
    "hitokoto": "句子内容",
    "from": "来源",
    "from_who": "作者"
  }
]'
                  help="支持 JSON 数组或单个对象"
                  :rows="10"
          />

          <div class="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <h4 class="mb-3 text-sm font-medium text-gray-700">字段映射</h4>
            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">
                  句子内容
                  <span class="text-red-400">*</span>
                </label>
                <select
                        v-model="batchImportForm.contentField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in availableKeys" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">作者</label>
                <select
                        v-model="batchImportForm.authorField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in availableKeys" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">来源</label>
                <select
                        v-model="batchImportForm.sourceField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in availableKeys" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
            </div>
          </div>

          <div class="mt-4">
            <div class="mb-2 flex items-center justify-between">
              <label class="text-sm font-medium text-gray-700">解析预览</label>
              <span class="text-xs text-gray-400">共 {{ parsedSentences.length }} 条</span>
            </div>
            <div class="max-h-60 overflow-y-auto rounded-md border border-gray-200">
              <div
                      v-if="parsedSentences.length === 0"
                      class="py-8 text-center text-sm text-gray-400"
              >
                请粘贴有效的 JSON 数据并选择字段映射
              </div>
              <div v-else class="divide-y divide-gray-100">
                <div
                        v-for="(item, index) in parsedSentences"
                        :key="index"
                        class="flex items-start justify-between px-4 py-3 hover:bg-gray-50"
                >
                  <div class="min-w-0 flex-1">
                    <div class="text-sm font-medium text-gray-900">
                      {{ item.content || '(无内容)' }}
                    </div>
                    <div class="mt-1 flex flex-wrap items-center gap-1.5">
                      <span
                              class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600"
                      >
                        作者：{{ item.author || '匿名' }}
                      </span>
                      <span
                              class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600"
                      >
                        来源：{{ item.source || '未知' }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <h4 class="mb-3 text-sm font-medium text-gray-700">上传 Excel 文件</h4>
            <input
                    accept=".xlsx"
                    class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm file:mr-3 file:rounded file:border-0 file:bg-gray-100 file:px-3 file:py-1.5 file:text-sm file:text-gray-700"
                    type="file"
                    @change="handleExcelFileChange"
            />
            <p class="mt-2 text-xs text-gray-500">
              仅支持 .xlsx。第一行作为列名。
            </p>
          </div>

          <div class="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <h4 class="mb-3 text-sm font-medium text-gray-700">字段映射</h4>
            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">
                  句子内容列
                  <span class="text-red-400">*</span>
                </label>
                <select
                        v-model="batchImportExcelForm.contentField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in excelColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">作者列</label>
                <select
                        v-model="batchImportExcelForm.authorField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in excelColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">来源列</label>
                <select
                        v-model="batchImportExcelForm.sourceField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in excelColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
            </div>
          </div>

          <!-- Excel 预览 -->
          <div class="mt-4">
            <div class="mb-2 flex items-center justify-between">
              <label class="text-sm font-medium text-gray-700">解析预览</label>
              <span class="text-xs text-gray-400">共 {{ excelPreview.length }} 条</span>
            </div>
            <div class="max-h-60 overflow-y-auto rounded-md border border-gray-200">
              <div v-if="!batchImportExcelFile" class="py-8 text-center text-sm text-gray-400">
                请选择 Excel 文件
              </div>
              <div v-else-if="!batchImportExcelForm.contentField"
                   class="py-8 text-center text-sm text-gray-400">
                请选择句子内容字段映射
              </div>
              <div v-else-if="excelPreview.length === 0"
                   class="py-8 text-center text-sm text-gray-400">
                没有解析到有效的句子数据
              </div>
              <div v-else class="divide-y divide-gray-100">
                <div
                        v-for="(item, index) in excelPreview"
                        :key="index"
                        class="flex items-start justify-between px-4 py-3 hover:bg-gray-50"
                >
                  <div class="min-w-0 flex-1">
                    <div class="text-sm font-medium text-gray-900">
                      {{ item.content || '(无内容)' }}
                    </div>
                    <div class="mt-1 flex flex-wrap items-center gap-1.5">
            <span class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600">
              作者：{{ item.author || '匿名' }}
            </span>
                      <span class="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600">
              来源：{{ item.source || '未知' }}
            </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="showBatchImportModal = false">取消</VButton>
          <VButton
                  type="secondary"
                  :loading="batchImporting"
                  :disabled="
              batchImportMode === 'json' ? parsedSentences.length === 0 : excelPreview.length === 0
            "
                  @click="handleBatchSave"
          >
            {{
              batchImportMode === 'json' ? `开始导入 (${parsedSentences.length} 条)` : `开始导入 (${excelPreview.length} 条)`
            }}
          </VButton>
        </div>
      </template>
    </VModal>
  </div>
</template>

<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  IconRefreshLine,
  IconUpload,
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
import {Delete, EditPen, View} from '@element-plus/icons-vue'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {categoryCoreApiClient, sentenceCoreApiClient} from '@/api'
import type {BatchCreateSentenceResult, Category, Sentence} from '@/api/generated'
import IconLike from '~icons/my-icons/like';
import * as XLSX from 'xlsx'

const xlsxRows = ref<any[][]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const sentences = ref<Sentence[]>([])
const categories = ref<Category[]>([])
let deletionRefetchTimer: ReturnType<typeof setInterval> | null = null

const selectedCategory = ref<string | undefined>(undefined)
const selectedSort = ref<string | undefined>(undefined)
const canManage = computed(() => utils.permission.has(['plugin:hitokoto-hub:manage']))

const showFormModal = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const editingSentenceName = ref('')
const editingOriginalSentence = ref<Sentence | null>(null)
const formData = ref({
  content: '',
  categoryName: '',
  author: '匿名',
  source: '未知',
  published: true,
})

const showBatchImportModal = ref(false)
const batchImporting = ref(false)
const batchImportMode = ref<'json' | 'excel'>('json')
const batchImportExcelFile = ref<File | null>(null)
const excelColumns = ref<string[]>([])
const batchImportForm = ref({
  jsonText: '',
  categoryName: '',
  contentField: '',
  authorField: '',
  sourceField: '',
})
const batchImportExcelForm = ref({
  contentField: '',
  authorField: '',
  sourceField: '',
})

const showCategoryFormModal = ref(false)
const isEditingCategory = ref(false)
const savingCategory = ref(false)
const editingCategory = ref<Category | null>(null)
const categoryFormData = ref({
  specName: '',
  description: '',
})

const getCategoryName = (categoryId: string): string => {
  const category = categories.value.find((c) => c.metadata.name === categoryId)
  return category?.spec.name || categoryId
}

const totalCategorySentenceCount = computed(() =>
        categories.value.reduce((sum, category) => sum + (category.status?.sentenceCount ?? 0), 0),
)

const activeCategoryName = computed(() => {
  if (!selectedCategory.value) {
    return '全部分类'
  }
  return getCategoryName(selectedCategory.value)
})

const handleCategorySelect = (categoryName?: string) => {
  if (selectedCategory.value === categoryName) {
    return
  }
  selectedCategory.value = categoryName
  page.value = 1
}

const availableKeys = computed(() => {
  const text = batchImportForm.value.jsonText.trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    const data = Array.isArray(parsed) ? parsed : [parsed]
    const firstObject = data.find((item: any) => item && typeof item === 'object')
    if (!firstObject) return []
    return Object.keys(firstObject)
  } catch {
    return []
  }
})

const parsedSentences = computed(() => {
  const text = batchImportForm.value.jsonText.trim()
  if (!text) return []
  try {
    let data: any[] = []
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      data = parsed
    } else if (typeof parsed === 'object' && parsed !== null) {
      data = [parsed]
    } else {
      return []
    }

    const contentField = batchImportForm.value.contentField
    const authorField = batchImportForm.value.authorField
    const sourceField = batchImportForm.value.sourceField

    return data
            .filter((item) => item && typeof item === 'object')
            .map((item) => ({
              content: contentField ? String(item[contentField] ?? '') : '',
              author: authorField ? String(item[authorField] ?? '') : '',
              source: sourceField ? String(item[sourceField] ?? '') : '',
            }))
            .filter((item) => item.content)
  } catch {
    return []
  }
})

const excelPreview = computed(() => {
  if (!batchImportExcelFile.value) return []
  if (!xlsxRows.value.length) return []

  const contentField = batchImportExcelForm.value.contentField
  const authorField = batchImportExcelForm.value.authorField
  const sourceField = batchImportExcelForm.value.sourceField

  if (!contentField) return []

  const rows = xlsxRows.value
  if (rows.length < 2) return []

  const headers = rows[0].map((h: any) => String(h ?? ''))
  const contentIndex = headers.indexOf(contentField)
  const authorIndex = authorField ? headers.indexOf(authorField) : -1
  const sourceIndex = sourceField ? headers.indexOf(sourceField) : -1

  if (contentIndex === -1) return []

  const result: Array<{ content: string; author: string; source: string }> = []
  for (let i = 1; i < rows.length; i++) {
    const row = rows[i]
    if (!row) continue
    const content = String(row[contentIndex] ?? '').trim()
    if (!content) continue
    result.push({
      content,
      author: authorIndex !== -1 ? String(row[authorIndex] ?? '').trim() : '',
      source: sourceIndex !== -1 ? String(row[sourceIndex] ?? '').trim() : '',
    })
  }
  return result
})

const isDeleting = (sentence: Sentence): boolean => {
  return !!sentence.metadata?.deletionTimestamp
}

const isDeletingCategory = (category: Category): boolean => {
  return !!category.metadata?.deletionTimestamp
}

const hasDeletingSentences = computed(() =>
        sentences.value.some((sentence) => isDeleting(sentence)),
)
const hasDeletingCategories = computed(() =>
        categories.value.some((category) => isDeletingCategory(category)),
)

const deletionRefetchInterval = computed(() =>
        hasDeletingSentences.value || hasDeletingCategories.value ? 1000 : false,
)

const categorySelectOptions = computed(() =>
        categories.value.map((c) => ({
          label: c.spec.name,
          value: c.metadata.name,
        })),
)

const initCategories = async () => {
  try {
    const {data} = await categoryCoreApiClient.category.listCategory({page: 1, size: 100})
    categories.value = data.items || []
  } catch (e) {
    console.error('获取分类列表失败', e)
  }
}

const fetchSentencesSilently = async () => {
  try {
    const params: any = {page: page.value, size: size.value}
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    if (selectedCategory.value) {
      params.categoryName = selectedCategory.value
    }
    if (selectedSort.value) {
      params.sort = Array.isArray(selectedSort.value)
              ? selectedSort.value[0]
              : selectedSort.value;
    }

    const {data} = await sentenceCoreApiClient.querySentences(params)
    sentences.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    console.error('Silent fetch failed', e)
  }
}

watch(
        deletionRefetchInterval,
        (interval) => {
          if (deletionRefetchTimer) {
            clearInterval(deletionRefetchTimer)
            deletionRefetchTimer = null
          }
          if (!interval) {
            return
          }
          deletionRefetchTimer = setInterval(async () => {
            if (hasDeletingCategories.value) {
              await initCategories()
            }
            if (hasDeletingSentences.value) {
              await fetchSentencesSilently()
            }
            if (!hasDeletingSentences.value && !hasDeletingCategories.value) {
              await initCategories()
              await fetchSentencesSilently()
              stopDeletionRefetch()
            }
          }, interval)
        },
        {immediate: true},
)

const stopDeletionRefetch = () => {
  if (deletionRefetchTimer) {
    clearInterval(deletionRefetchTimer)
    deletionRefetchTimer = null
  }
}

const fetchSentences = async () => {
  loading.value = true
  try {
    const params: any = {page: page.value, size: size.value}
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    if (selectedCategory.value) {
      params.categoryName = selectedCategory.value
    }
    if (selectedSort.value) {
      params.sort = Array.isArray(selectedSort.value)
              ? selectedSort.value[0]
              : selectedSort.value;
    }

    const {data} = await sentenceCoreApiClient.querySentences(params)
    sentences.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    console.error('获取句子列表失败', e)
    Toast.error('加载句子列表失败')
  } finally {
    loading.value = false
  }
}

// 监听 keyword 清空时恢复列表
watch(keyword, (newVal) => {
  if (!newVal.trim()) {
    page.value = 1
    fetchSentences()
  }
})

const handleSearch = async () => {
  page.value = 1
  await fetchSentences()
}

const refreshData = () => {
  initCategories()
  if (keyword.value.trim()) {
    handleSearch()
  } else {
    fetchSentences()
  }
}

const handleClearFilters = () => {
  selectedCategory.value = undefined
  selectedSort.value = undefined
  keyword.value = ''
  page.value = 1
  fetchSentences()
}

const hasFilters = computed(() => {
  return Boolean(selectedCategory.value || selectedSort.value || keyword.value)
})

// 分页和筛选变化时，根据是否有搜索关键词决定调用哪个方法
watch(page, () => {
  if (keyword.value.trim()) {
    handleSearch()
  } else {
    fetchSentences()
  }
})

watch(size, () => {
  page.value = 1
  if (keyword.value.trim()) {
    handleSearch()
  } else {
    fetchSentences()
  }
})

watch([selectedCategory, selectedSort], () => {
  page.value = 1
  if (keyword.value.trim()) {
    handleSearch()
  } else {
    fetchSentences()
  }
})

const handleCreate = () => {
  isEditing.value = false
  editingSentenceName.value = ''
  editingOriginalSentence.value = null
  formData.value = {
    content: '',
    categoryName: categories.value[0]?.metadata.name || '',
    author: '匿名',
    source: '未知',
    published: true,
  }
  showFormModal.value = true
}

const handleBatchImport = async (mode: 'json' | 'excel' = 'json') => {
  await initCategories()
  batchImportForm.value = {
    jsonText: '',
    categoryName: categories.value[0]?.metadata.name || '',
    contentField: '',
    authorField: '',
    sourceField: '',
  }
  batchImportMode.value = mode
  batchImportExcelFile.value = null
  excelColumns.value = []
  xlsxRows.value = [] // 清空缓存
  batchImportExcelForm.value = {
    contentField: '',
    authorField: '',
    sourceField: '',
  }
  showBatchImportModal.value = true
}

const handleCreateCategory = () => {
  isEditingCategory.value = false
  editingCategory.value = null
  categoryFormData.value = {
    specName: '',
    description: '',
  }
  showCategoryFormModal.value = true
}

const handleEditCategory = (category: Category) => {
  isEditingCategory.value = true
  editingCategory.value = category
  categoryFormData.value = {
    specName: category.spec.name || '',
    description: category.spec.description || '',
  }
  showCategoryFormModal.value = true
}

const handleSaveCategory = async () => {
  const specName = categoryFormData.value.specName.trim()
  const description = categoryFormData.value.description.trim()

  if (!specName) {
    Toast.warning('请输入分类名称')
    return
  }

  savingCategory.value = true
  try {
    if (isEditingCategory.value && editingCategory.value) {
      // 重新获取最新数据，防止多次修改导致版本冲突
      const { data: latestCategory } = await categoryCoreApiClient.category.getCategory({
        name: editingCategory.value.metadata.name,
      })
      const updated: Category = {
        ...latestCategory,
        spec: {
          ...latestCategory.spec,
          name: specName,
          description: description || undefined,
        },
      }
      await categoryCoreApiClient.category.updateCategory({
        name: editingCategory.value.metadata.name,
        category: updated,
      })
      Toast.success('更新分类成功')
    } else {
      const category: Category = {
        apiVersion: 'hitokotohub.puresky.top/v1alpha1',
        kind: 'Category',
        metadata: {
          generateName: 'category-',
          name: '',
        },
        spec: {
          name: specName,
          description: description || undefined,
        },
      }
      await categoryCoreApiClient.category.createCategory({category})
      Toast.success('创建分类成功')
    }

    showCategoryFormModal.value = false
    await initCategories()
  } catch (e) {
    console.error('保存分类失败', e)
    Toast.error(isEditingCategory.value ? '更新分类失败' : '创建分类失败')
  } finally {
    savingCategory.value = false
  }
}

const handleDeleteCategory = (category: Category) => {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除分类「${category.spec.name}」吗？该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await categoryCoreApiClient.category.deleteCategory({name: category.metadata.name})
        if (selectedCategory.value === category.metadata.name) {
          selectedCategory.value = undefined
        }
        Toast.success('删除分类成功')
        await initCategories()
        await fetchSentences()
      } catch (e) {
        console.error('删除分类失败', e)
        Toast.error('删除分类失败')
      }
    },
  })
}

const buildSentence = (
        content: string,
        categoryName: string,
        author?: string,
        source?: string,
): Sentence => ({
  apiVersion: 'hitokotohub.puresky.top/v1alpha1',
  kind: 'Sentence',
  metadata: {generateName: 'sentence-', name: ''},
  spec: {content, categoryName, author: author || '匿名', source: source || '未知'},
})

const batchCreate = async (sentenceList: Sentence[]): Promise<BatchCreateSentenceResult> => {
  const {data} = await sentenceCoreApiClient.sentence.batchCreateSentence({
    sentence: sentenceList,
  })
  return data as BatchCreateSentenceResult
}

const handleExcelFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    batchImportExcelFile.value = null
    xlsxRows.value = []
    excelColumns.value = []
    return
  }

  batchImportExcelFile.value = file

  const buffer = await file.arrayBuffer()
  const data = new Uint8Array(buffer)
  const workbook = XLSX.read(data, {type: 'array'})
  const sheet = workbook.Sheets[workbook.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json<any[]>(sheet, {header: 1})

  xlsxRows.value = rows

  if (rows.length < 2) {
    excelColumns.value = []
    return
  }

  excelColumns.value = rows[0].map((h: any) => String(h ?? ''))
}

const handleSave = async () => {
  if (!formData.value.content || !formData.value.categoryName) {
    Toast.warning('请填写句子内容和分类')
    return
  }
  saving.value = true
  try {
    if (isEditing.value && editingOriginalSentence.value) {
      // 重新获取最新数据，防止多次修改导致版本冲突
      const { data: latestSentence } = await sentenceCoreApiClient.sentence.getSentence({
        name: editingSentenceName.value,
      })
      const updated: Sentence = {
        ...latestSentence,
        spec: {
          ...latestSentence.spec,
          content: formData.value.content,
          categoryName: formData.value.categoryName,
          author: formData.value.author,
          source: formData.value.source,
        },
        status: {...latestSentence.status, published: formData.value.published},
      }
      await sentenceCoreApiClient.sentence.updateSentence({
        name: editingSentenceName.value,
        sentence: updated,
      })
      Toast.success('更新成功')
    } else {
      const sentence = buildSentence(
              formData.value.content,
              formData.value.categoryName,
              formData.value.author,
              formData.value.source,
      )
      await batchCreate([sentence])
      Toast.success('创建成功')
    }
    showFormModal.value = false
    await fetchSentencesSilently()
    await initCategories()
  } catch (e) {
    console.error('保存失败', e)
    Toast.error(isEditing.value ? '更新失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

const handleBatchSave = async () => {
  if (!batchImportForm.value.categoryName) {
    Toast.warning('请选择目标分类')
    return
  }

  if (batchImportMode.value === 'json' && parsedSentences.value.length === 0) {
    Toast.warning('没有解析到有效的句子数据')
    return
  }
  if (batchImportMode.value === 'excel' && excelPreview.value.length === 0) {
    Toast.warning('没有解析到有效的句子数据')
    return
  }

  batchImporting.value = true
  try {
    let result: BatchCreateSentenceResult
    if (batchImportMode.value === 'excel') {
      const sentenceList = excelPreview.value.map((item) =>
              buildSentence(item.content, batchImportForm.value.categoryName, item.author, item.source),
      )
      result = await batchCreate(sentenceList)
    } else {
      const sentenceList = parsedSentences.value.map((item) =>
              buildSentence(item.content, batchImportForm.value.categoryName, item.author, item.source),
      )
      result = await batchCreate(sentenceList)
    }
    Toast.success(`导入完成！成功: ${result.success || 0}，失败: ${result.failed || 0}`)
    showBatchImportModal.value = false
    await fetchSentences()
    await initCategories()
  } catch (e) {
    console.error('批量导入失败', e)
    Toast.error('批量导入失败')
  } finally {
    batchImporting.value = false
  }
}

const handleEdit = (sentence: Sentence) => {
  isEditing.value = true
  editingSentenceName.value = sentence.metadata.name
  editingOriginalSentence.value = sentence
  formData.value = {
    content: sentence.spec.content,
    categoryName: sentence.spec.categoryName,
    author: sentence.spec.author || '匿名',
    source: sentence.spec.source || '未知',
    published: sentence.status?.published ?? true,
  }
  showFormModal.value = true
}

const handleDelete = (sentence: Sentence) => {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除句子「${sentence.spec.content.slice(0, 30)}${sentence.spec.content.length > 30 ? '...' : ''}」吗？该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await sentenceCoreApiClient.sentence.deleteSentence({name: sentence.metadata.name})
        Toast.success('删除成功')
        await fetchSentencesSilently()
      } catch (e) {
        console.error('删除失败', e)
        Toast.error('删除失败')
      }
    },
  })
}

onMounted(() => {
  initCategories()
  fetchSentences()
})

onUnmounted(() => {
  stopDeletionRefetch()
})
</script>

<style scoped>
.sentence-workbench {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 420px;
}

.sentence-card-header {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 16px;
  padding: 12px 16px;
}

.sentence-category-pane {
  border-right: 1px solid #eaecf0;
  background: #fbfcfe;
}

.sentence-category-pane__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 12px 16px;
  border-bottom: 1px solid #eaecf0;
}

.category-nav {
  padding: 10px 12px;
}

.category-nav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 42px;
  gap: 8px;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #4b5563;
  text-align: left;
  transition: background-color 0.12s ease,
  border-color 0.12s ease,
  box-shadow 0.12s ease,
  color 0.12s ease;
}

.category-nav__item:hover {
  background: #f9fafb;
  color: #111827;
}

.category-nav__item--active {
  border-color: #d9dee8;
  background: #fff;
  color: #111827;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.category-nav__item--editable:hover .category-nav__actions,
.category-nav__item--editable:focus-within .category-nav__actions {
  opacity: 1;
}

.category-nav__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.category-nav__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.category-nav__count {
  font-size: 12px;
  color: #6b7280;
}

.category-nav__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.category-nav__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  color: #6b7280;
}

.category-nav__action:hover {
  background: #e5e7eb;
  color: #111827;
}

.category-nav__action--danger:hover {
  background: #fee2e2;
  color: #b91c1c;
}

.sentence-list-pane {
  min-width: 0;
  background: #fff;
}

.sentence-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 64px;
  padding: 12px 16px;
  border-bottom: 1px solid #eaecf0;
  background: #f9fafb;
}

.sentence-list-pagination {
  padding: 12px 16px;
  border-top: 1px solid #eaecf0;
}

@media (max-width: 900px) {
  .sentence-card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .sentence-workbench {
    grid-template-columns: 1fr;
  }

  .sentence-category-pane {
    border-right: 0;
    border-bottom: 1px solid #eaecf0;
  }

  .sentence-list-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (hover: none) {
  .category-nav__actions {
    opacity: 1;
  }
}

.form-modal-body {
  padding: 4px 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.batch-mode-button {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;
}

.batch-mode-button:hover {
  background: #e5e7eb;
  color: #374151;
}

.batch-mode-button--active {
  background: #fff;
  color: #111827;
  border-color: #d1d5db;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
</style>
