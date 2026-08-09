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
              导入
            </VButton>
            <VButton size="sm" @click="showExportModal = true">
              <template #icon>
                <el-icon :size="16"><Download /></el-icon>
              </template>
              导出
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
                      v-for="category in regularCategories"
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
                            : `${category.sentenceCount ?? 0} 条句子`
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

              <!-- 未分类：系统内置分类 -->
              <div
                      v-if="uncategorizedCategory"
                      :class="{ 'category-nav__item--active': selectedCategory === uncategorizedCategory.metadata.name }"
                      class="category-nav__item category-nav__item--editable"
                      role="button"
                      tabindex="0"
                      @click="handleCategorySelect(uncategorizedCategory.metadata.name)"
                      @keydown.enter="handleCategorySelect(uncategorizedCategory.metadata.name)"
              >
              <span class="category-nav__text">
                <span class="category-nav__name">{{ uncategorizedCategory.spec.name }}</span>
                <span class="category-nav__count">{{ uncategorizedCategory.sentenceCount ?? 0 }} 条句子</span>
              </span>
                <span class="category-nav__actions">
                <button
                        v-tooltip="'清空未分类句子'"
                        class="category-nav__action category-nav__action--danger"
                        type="button"
                        @click.stop="handleClearUncategorized"
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

          <!-- 批量操作工具栏：选择状态反馈 + 批量操作按钮（遵循 Halo 设计规范：VSpace 间距、VButton ghost、Halo 标准色板） -->
          <div
                  v-if="canManage && sentences.length > 0"
                  class="sentence-batch-bar"
          >
            <VSpace spacing="sm" align="center">
              <el-checkbox
                      :model-value="isAllSelectedOnPage"
                      :indeterminate="isIndeterminateOnPage"
                      :disabled="batchOperating"
                      @change="toggleAllOnPage"
              >
                <span class="batch-count">
                  <template v-if="selectedCount > 0">已选择 {{ selectedCount }} 项</template>
                  <template v-else>全选当前页</template>
                </span>
              </el-checkbox>
              <VButton
                      v-if="hasSelection"
                      size="sm"
                      type="secondary"
                      :disabled="batchOperating"
                      @click="clearSelection"
              >
                取消选择
              </VButton>
            </VSpace>
            <VSpace spacing="sm" align="center" wrap>
              <el-select
                      v-model="batchMoveCategory"
                      placeholder="选择目标分类"
                      size="small"
                      class="batch-category-select"
                      :disabled="batchOperating"
                      filterable
              >
                <el-option
                        v-for="c in categories"
                        :key="c.metadata.name"
                        :label="c.spec.name"
                        :value="c.metadata.name"
                />
              </el-select>
              <VButton
                      size="sm"
                      :disabled="!canBatchMove || batchOperating"
                      :loading="batchOperating && batchOperatingType === 'move'"
                      @click="handleBatchMove"
              >
                移动到分类
              </VButton>
              <VButton
                      size="sm"
                      :disabled="!hasSelection || batchOperating"
                      :loading="batchOperating && batchOperatingType === 'publish'"
                      @click="handleBatchPublish(true)"
              >
                发布
              </VButton>
              <VButton
                      size="sm"
                      :disabled="!hasSelection || batchOperating"
                      :loading="batchOperating && batchOperatingType === 'unpublish'"
                      @click="handleBatchPublish(false)"
              >
                取消发布
              </VButton>
              <VButton
                      size="sm"
                      type="danger"
                      :disabled="!hasSelection || batchOperating"
                      :loading="batchOperating && batchOperatingType === 'delete'"
                      @click="handleBatchDelete"
              >
                删除
              </VButton>
            </VSpace>
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
                    <div v-if="canManage" class="sentence-row-checkbox">
                      <el-checkbox
                              :model-value="isSelected(sentence.metadata.name)"
                              :disabled="batchOperating || isDeleting(sentence)"
                              @change="toggle(sentence.metadata.name)"
                              @click.stop
                      />
                    </div>
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

        <FormKit
          v-model="formData.linkType"
          type="select"
          label="跳转方式"
          placeholder="请选择跳转方式"
          help="设置后，前台展示该句子时点击可跳转到对应地址"
          :options="[
            { label: '不跳转', value: 'none' },
            { label: '自定义链接', value: 'url' },
            { label: '关联文章', value: 'post' },
          ]"
        />
        <FormKit
          v-if="formData.linkType === 'url'"
          v-model="formData.linkUrl"
          type="text"
          label="链接地址"
          placeholder="请输入完整 URL，如 https://example.com/article"
        />
        <div v-if="formData.linkType === 'post'" class="form-field-wrapper">
          <label class="form-field-label">关联文章</label>
          <el-select
            v-model="formData.postName"
            filterable
            clearable
            placeholder="选择文章（按发布时间倒序，支持搜索）"
            :loading="postSearchLoading"
            @visible-change="handlePostDropdownVisible"
            class="w-full post-select"
            size="default"
          >
            <el-option
              v-for="post in postOptions"
              :key="post.name"
              :label="post.title"
              :value="post.name"
            />
            <template #empty>
              <div class="text-center text-xs text-gray-400 py-2">
                {{ postSearchLoading ? '加载中...' : (postsLoaded ? '未找到匹配的文章' : '点击下拉加载文章列表') }}
              </div>
            </template>
          </el-select>
        </div>
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
          <button
                  :class="{ 'batch-mode-button--active': batchImportMode === 'csv' }"
                  class="batch-mode-button"
                  type="button"
                  @click="batchImportMode = 'csv'"
          >
            CSV
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

        <template v-else-if="batchImportMode === 'excel'">
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

        <template v-else-if="batchImportMode === 'csv'">
          <div class="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <h4 class="mb-3 text-sm font-medium text-gray-700">上传 CSV 文件</h4>
            <input
                    accept=".csv"
                    class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm file:mr-3 file:rounded file:border-0 file:bg-gray-100 file:px-3 file:py-1.5 file:text-sm file:text-gray-700"
                    type="file"
                    @change="handleCsvFileChange"
            />
            <p class="mt-2 text-xs text-gray-500">
              仅支持 .csv，UTF-8 编码。第一行作为列名。
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
                        v-model="batchImportCsvForm.contentField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in csvColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">作者列</label>
                <select
                        v-model="batchImportCsvForm.authorField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in csvColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-gray-500">来源列</label>
                <select
                        v-model="batchImportCsvForm.sourceField"
                        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">不映射</option>
                  <option v-for="key in csvColumns" :key="key" :value="key">{{ key }}</option>
                </select>
              </div>
            </div>
          </div>

          <!-- CSV 预览 -->
          <div class="mt-4">
            <div class="mb-2 flex items-center justify-between">
              <label class="text-sm font-medium text-gray-700">解析预览</label>
              <span class="text-xs text-gray-400">共 {{ csvPreview.length }} 条</span>
            </div>
            <div class="max-h-60 overflow-y-auto rounded-md border border-gray-200">
              <div v-if="!batchImportCsvFile" class="py-8 text-center text-sm text-gray-400">
                请选择 CSV 文件
              </div>
              <div v-else-if="!batchImportCsvForm.contentField"
                   class="py-8 text-center text-sm text-gray-400">
                请选择句子内容字段映射
              </div>
              <div v-else-if="csvPreview.length === 0"
                   class="py-8 text-center text-sm text-gray-400">
                没有解析到有效的句子数据
              </div>
              <div v-else class="divide-y divide-gray-100">
                <div
                        v-for="(item, index) in csvPreview"
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
              batchImportMode === 'json' ? parsedSentences.length === 0 : batchImportMode === 'excel' ? excelPreview.length === 0 : csvPreview.length === 0
            "
                  @click="handleBatchSave"
          >
            {{
              batchImportMode === 'json' ? `开始导入 (${parsedSentences.length} 条)` : batchImportMode === 'excel' ? `开始导入 (${excelPreview.length} 条)` : `开始导入 (${csvPreview.length} 条)`
            }}
          </VButton>
        </div>
      </template>
    </VModal>

    <!-- 导出句子弹窗 -->
    <VModal
      v-model:visible="showExportModal"
      title="导出句子"
      :width="480"
    >
      <div class="form-modal-body">
        <FormKit
          v-model="exportForm.format"
          type="select"
          label="导出格式"
          :options="[
            { label: 'JSON', value: 'json' },
            { label: 'Excel (.xlsx)', value: 'excel' },
          ]"
        />
        <div class="mt-4">
          <label class="block text-sm font-medium text-gray-700 mb-2.5">选择分类</label>
          <div class="export-category-list">
            <label class="export-category-item export-category-item--all">
              <input
                type="checkbox"
                :checked="exportForm.selectAll"
                @change="handleExportSelectAll"
              />
              <span class="export-category-name">全部分类</span>
              <span class="export-category-count">{{ total }} 条</span>
            </label>
            <label
              v-for="category in regularCategories"
              :key="category.metadata.name"
              class="export-category-item"
            >
              <input
                type="checkbox"
                :value="category.metadata.name"
                :checked="exportForm.categoryNames.includes(category.metadata.name)"
                @change="handleExportToggleCategory(category.metadata.name)"
              />
              <span class="export-category-name">{{ category.spec.name }}</span>
              <span class="export-category-count">{{ category.sentenceCount ?? 0 }} 条</span>
            </label>
            <label
              v-if="uncategorizedCategory"
              class="export-category-item"
            >
              <input
                type="checkbox"
                :value="uncategorizedCategory.metadata.name"
                :checked="exportForm.categoryNames.includes(uncategorizedCategory.metadata.name)"
                @change="handleExportToggleCategory(uncategorizedCategory.metadata.name)"
              />
              <span class="export-category-name">{{ uncategorizedCategory.spec.name }}</span>
              <span class="export-category-count">{{ uncategorizedCategory.sentenceCount ?? 0 }} 条</span>
            </label>
          </div>
          <div class="mt-2 text-xs text-gray-400">
            {{ exportForm.selectAll ? '将导出所有分类的句子' : exportForm.categoryNames.length > 0 ? `已选择 ${exportForm.categoryNames.length} 个分类` : '请至少选择一个分类' }}
          </div>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <VButton
            type="secondary"
            :disabled="exporting"
            @click="showExportModal = false"
          >
            取消
          </VButton>
          <VButton
            :loading="exporting"
            :disabled="!canExport"
            @click="handleExport"
          >
            {{ exportForm.selectAll ? '导出全部' : `导出所选 (${exportForm.categoryNames.length})` }}
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
  VButton,
  VCard,
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
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import {axiosInstance} from '@halo-dev/api-client'
import {utils} from '@halo-dev/ui-shared'
import {Delete, Download, EditPen, View} from '@element-plus/icons-vue'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import type {CategoryWithCount} from '@/api'
import {categoryCoreApiClient, sentenceCoreApiClient} from '@/api'
import type {BatchCreateSentenceResult, Category, Sentence} from '@/api/generated'
import IconLike from '~icons/my-icons/like';
import * as XLSX from 'xlsx'
import {useToast} from '@/composables/useToast'
import {
  runWithConcurrency,
  summarizeBatchResult,
  useBatchSelection,
} from '@/composables/useBatchSelection'

const toast = useToast()

const xlsxRows = ref<any[][]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const sentences = ref<Sentence[]>([])
const categories = ref<CategoryWithCount[]>([])
let deletionRefetchTimer: ReturnType<typeof setInterval> | null = null

const selectedCategory = ref<string | undefined>(undefined)
const selectedSort = ref<string | undefined>(undefined)
const canManage = computed(() => utils.permission.has(['plugin:hitokoto-hub:manage']))

// 批量选择状态：使用 Set 存储 ID，O(1) 查找/增删。
// resetWatch 监听分页/筛选/排序变化时自动清空选择，避免"看不到却已选"的混淆。
const {
  selectedIdList,
  selectedCount,
  hasSelection,
  isSelected,
  isAllSelectedOnPage,
  isIndeterminateOnPage,
  toggle,
  toggleAllOnPage,
  clear: clearSelection,
} = useBatchSelection<Sentence>({
  getId: (s) => s.metadata.name,
  items: sentences,
  resetWatch: [page, size, selectedCategory, selectedSort],
})

// 批量操作状态
const batchMoveCategory = ref('')
const batchOperating = ref(false)
const batchOperatingType = ref<'move' | 'publish' | 'unpublish' | 'delete' | ''>('')

const canBatchMove = computed(() => hasSelection.value && !!batchMoveCategory.value)

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
  linkType: 'none' as 'none' | 'url' | 'post',
  linkUrl: '',
  postName: '',
})

// 文章搜索相关状态
const allPosts = ref<{ name: string; title: string }[]>([])
const postOptions = ref<{ name: string; title: string }[]>([])
const postSearchLoading = ref(false)
const postsLoaded = ref(false)

/**
 * 分页拉取全部已发布文章（Content API），缓存到 allPosts
 * Content API /apis/content.halo.run/v1alpha1/posts 不支持 keyword 搜索，
 * 所以一次性加载全部文章到前端，由 el-select filterable 本地过滤。
 */
const fetchAllPosts = async () => {
  if (postsLoaded.value) {
    postOptions.value = allPosts.value
    return
  }
  postSearchLoading.value = true
  try {
    const result: { name: string; title: string }[] = []
    const pageSize = 100
    let page = 1
    let hasNext = true
    while (hasNext) {
      const { data } = await axiosInstance.get('/apis/content.halo.run/v1alpha1/posts', {
        params: {
          page,
          size: pageSize,
          sort: 'spec.publishTime,desc',
        },
      })
      const items = data.items || []
      for (const p of items) {
        if (p.spec && p.status?.permalink) {
          result.push({
            name: p.metadata.name,
            title: p.spec.title || '(无标题)',
          })
        }
      }
      hasNext = data.hasNext === true && items.length === pageSize
      page++
      /* 安全上限：最多拉取 20 页（2000 篇），防止极端情况死循环 */
      if (page > 20) break
    }
    allPosts.value = result
    postOptions.value = result
    postsLoaded.value = true
  } catch (e) {
    console.error('加载文章列表失败', e)
    toast.error('加载文章列表失败')
  } finally {
    postSearchLoading.value = false
  }
}

const handlePostDropdownVisible = (visible: boolean) => {
  if (visible && !postsLoaded.value) {
    fetchAllPosts()
  }
}

// 监听linkType变化，清空不相关字段，切换到"关联文章"时预加载文章列表
watch(() => formData.value.linkType, (newType) => {
  if (newType !== 'url') {
    formData.value.linkUrl = ''
  }
  if (newType !== 'post') {
    formData.value.postName = ''
  } else {
    // 切换到"关联文章"时自动加载文章列表
    fetchAllPosts()
  }
})

const showBatchImportModal = ref(false)
const batchImporting = ref(false)
const batchImportMode = ref<'json' | 'excel' | 'csv'>('json')
const batchImportExcelFile = ref<File | null>(null)
const excelColumns = ref<string[]>([])
const batchImportCsvFile = ref<File | null>(null)
const csvColumns = ref<string[]>([])
const csvRows = ref<string[][]>([])
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
const batchImportCsvForm = ref({
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
  if (category) return category.spec.name
  if (categoryId === UNCATEGORIZED_NAME) return '未分类'
  return categoryId
}

const totalCategorySentenceCount = computed(() =>
        categories.value.reduce((sum, category) => sum + (category.sentenceCount ?? 0), 0),
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

// 判断是否为普通对象（非 null、非数组）
const isPlainObject = (v: any): boolean =>
  v !== null && typeof v === 'object' && !Array.isArray(v)

// 递归将多层级的对象扁平化为点分路径的叶子键，如 spec.content、metadata.name
// 数组若首元素为普通对象，则展开为 items.0.content 形式的索引路径；否则作为叶子节点
const flattenKeys = (obj: any, prefix = ''): string[] => {
  if (!isPlainObject(obj)) return []
  const keys: string[] = []
  for (const key of Object.keys(obj)) {
    const value = obj[key]
    const path = prefix ? `${prefix}.${key}` : key
    if (Array.isArray(value)) {
      if (value.length && isPlainObject(value[0])) {
        value.forEach((item, index) => {
          keys.push(...flattenKeys(item, `${path}.${index}`))
        })
      } else {
        keys.push(path)
      }
    } else if (isPlainObject(value)) {
      keys.push(...flattenKeys(value, path))
    } else {
      keys.push(path)
    }
  }
  return keys
}

// 按点分路径从对象中取值，如 getNestedValue(item, 'spec.content') -> item.spec.content
const getNestedValue = (obj: any, path: string): any => {
  if (!path) return undefined
  const segments = path.split('.')
  let current: any = obj
  for (const seg of segments) {
    // 仅保留 null/undefined 检查，允许数组（typeof 为 'object'）按索引继续取值
    if (current == null) return undefined
    current = current[seg]
  }
  return current
}

const availableKeys = computed(() => {
  const text = batchImportForm.value.jsonText.trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    const data = Array.isArray(parsed) ? parsed : [parsed]
    const firstObject = data.find((item: any) => isPlainObject(item))
    if (!firstObject) return []
    return flattenKeys(firstObject)
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
    } else if (isPlainObject(parsed)) {
      data = [parsed]
    } else {
      return []
    }

    const contentField = batchImportForm.value.contentField
    const authorField = batchImportForm.value.authorField
    const sourceField = batchImportForm.value.sourceField

    return data
            .filter((item) => isPlainObject(item))
            .map((item) => ({
              content: contentField ? String(getNestedValue(item, contentField) ?? '') : '',
              author: authorField ? String(getNestedValue(item, authorField) ?? '') : '',
              source: sourceField ? String(getNestedValue(item, sourceField) ?? '') : '',
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

const csvPreview = computed(() => {
  if (!batchImportCsvFile.value) return []
  if (!csvRows.value.length) return []

  const contentField = batchImportCsvForm.value.contentField
  const authorField = batchImportCsvForm.value.authorField
  const sourceField = batchImportCsvForm.value.sourceField

  if (!contentField) return []

  const rows = csvRows.value
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

const UNCATEGORIZED_NAME = 'uncategorized'

const regularCategories = computed(() =>
        categories.value.filter((c) => c.metadata.name !== UNCATEGORIZED_NAME),
)

const uncategorizedCategory = computed(() =>
        categories.value.find((c) => c.metadata.name === UNCATEGORIZED_NAME) ?? null,
)

const isUncategorizedSelected = computed(() =>
        selectedCategory.value === UNCATEGORIZED_NAME,
)

const uncategorizedSentenceCount = computed(() =>
        uncategorizedCategory.value?.sentenceCount ?? 0,
)

const initCategories = async () => {
  try {
    const {data} = await categoryCoreApiClient.listCategoriesWithCounts({page: 1, size: 100})
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
    console.error('静默刷新句子列表失败', e)
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
    toast.error('加载句子列表失败')
  } finally {
    loading.value = false
  }
}

// 监听 keyword 清空时恢复列表
watch(keyword, (newVal) => {
  if (!newVal.trim()) {
    page.value = 1
    clearSelection()
    fetchSentences()
  }
})

const handleSearch = async () => {
  page.value = 1
  clearSelection()
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
  clearSelection()
  page.value = 1
  fetchSentences()
}

const hasFilters = computed(() => {
  return Boolean(selectedCategory.value || selectedSort.value || keyword.value)
})

// 分页和筛选变化时，根据是否有搜索关键词决定调用哪个方法
watch(page, () => {
  fetchSentences()
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
    linkType: 'none',
    linkUrl: '',
    postName: '',
  }
  // 新建时如果已有缓存，直接使用；切换到"关联文章"时watch会自动填充
  if (postsLoaded.value) {
    postOptions.value = allPosts.value
  } else {
    postOptions.value = []
  }
  showFormModal.value = true
}

const handleBatchImport = async (mode: 'json' | 'excel' | 'csv' = 'json') => {
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
  batchImportCsvFile.value = null
  csvColumns.value = []
  csvRows.value = []
  batchImportCsvForm.value = {
    contentField: '',
    authorField: '',
    sourceField: '',
  }
  showBatchImportModal.value = true
}

const showExportModal = ref(false)
const exporting = ref(false)
const exportForm = ref<{ format: string; categoryNames: string[]; selectAll: boolean }>({
  format: 'json',
  categoryNames: [],
  selectAll: true,
})

const canExport = computed(() =>
  exportForm.value.selectAll || exportForm.value.categoryNames.length > 0
)

const handleExportSelectAll = () => {
  exportForm.value.selectAll = !exportForm.value.selectAll
  if (exportForm.value.selectAll) {
    exportForm.value.categoryNames = []
  }
}

const handleExportToggleCategory = (name: string) => {
  // 选择具体分类时自动取消"全部分类"
  exportForm.value.selectAll = false
  const idx = exportForm.value.categoryNames.indexOf(name)
  if (idx >= 0) {
    exportForm.value.categoryNames.splice(idx, 1)
  } else {
    exportForm.value.categoryNames.push(name)
  }
}

const handleExport = async () => {
  if (!canExport.value) return
  try {
    exporting.value = true
    const params = new URLSearchParams()
    params.set('format', exportForm.value.format)
    if (!exportForm.value.selectAll && exportForm.value.categoryNames.length > 0) {
      params.set('categoryName', exportForm.value.categoryNames.join(','))
    }
    const url = `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence/-/export?${params.toString()}`
    const res = await axiosInstance.get(url, { responseType: 'blob' })
    const blob = new Blob([res.data])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    const ext = exportForm.value.format === 'excel' ? 'xlsx' : 'json'
    const prefix = exportForm.value.selectAll ? 'all' : exportForm.value.categoryNames.join('+')
    link.download = `hitokoto-${prefix}-${Date.now()}.${ext}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    showExportModal.value = false
    const desc = exportForm.value.selectAll ? '全部句子' : `${exportForm.value.categoryNames.length} 个分类`
    toast.success(`已导出 ${desc} 为 ${exportForm.value.format.toUpperCase()}`)
  } catch (e) {
    console.error('导出失败', e)
    toast.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
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
    toast.warning('请输入分类名称')
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
      toast.success('更新分类成功')
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
      toast.success('创建分类成功')
    }

    showCategoryFormModal.value = false
    await initCategories()
  } catch (e) {
    console.error('保存分类失败', e)
    toast.error(isEditingCategory.value ? '更新分类失败' : '创建分类失败')
  } finally {
    savingCategory.value = false
  }
}

const handleDeleteCategory = (category: Category) => {
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除分类「${category.spec.name}」吗？该分类下的句子将归入「未分类」，该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await categoryCoreApiClient.category.deleteCategory({name: category.metadata.name})
        if (selectedCategory.value === category.metadata.name) {
          selectedCategory.value = undefined
        }
        toast.success('删除分类成功')
        await initCategories()
        await fetchSentences()
      } catch (e) {
        console.error('删除分类失败', e)
        toast.error('删除分类失败')
      }
    },
  })
}

const handleClearUncategorized = () => {
  Dialog.warning({
    title: '清空未分类句子',
    description: '确定要清空所有未分类的句子吗？这些句子将被永久删除，该操作不可撤销。',
    confirmType: 'danger',
    confirmText: '清空',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        const count = uncategorizedSentenceCount.value
        const {data: deletedCount} = await sentenceCoreApiClient.clearUncategorizedSentences()
        toast.success(`已清空 ${deletedCount} 条未分类句子`)
        if (selectedCategory.value === UNCATEGORIZED_NAME) {
          await fetchSentences()
        }
        await initCategories()
      } catch (e) {
        console.error('清空未分类句子失败', e)
        toast.error('清空未分类句子失败')
      }
    },
  })
}

const buildSentence = (
        content: string,
        categoryName: string,
        author?: string,
        source?: string,
        linkUrl?: string,
        postName?: string,
): Sentence => ({
  apiVersion: 'hitokotohub.puresky.top/v1alpha1',
  kind: 'Sentence',
  metadata: {generateName: 'sentence-', name: ''},
  spec: {
    content,
    categoryName,
    author: author || '匿名',
    source: source || '未知',
    linkUrl: linkUrl || undefined,
    postName: postName || undefined,
  } as any,
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

const handleCsvFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    batchImportCsvFile.value = null
    csvRows.value = []
    csvColumns.value = []
    return
  }

  batchImportCsvFile.value = file

  try {
    const text = await file.text()
    // 去除 UTF-8 BOM
    const cleanText = text.startsWith('\uFEFF') ? text.substring(1) : text
    const lines = cleanText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
    const rows: string[][] = []
    for (const line of lines) {
      if (!line.trim()) continue
      rows.push(parseCsvLine(line))
    }
    csvRows.value = rows

    if (rows.length < 1) {
      csvColumns.value = []
      return
    }

    csvColumns.value = rows[0].map((h: string) => h.trim())
  } catch (e) {
    console.error('CSV 解析失败', e)
    toast.error('CSV 文件解析失败，请检查文件编码和格式')
    csvRows.value = []
    csvColumns.value = []
  }
}

/**
 * 解析单行 CSV，支持 RFC 4180 引号转义。
 */
const parseCsvLine = (line: string): string[] => {
  const fields: string[] = []
  let field = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQuotes) {
      if (c === '"') {
        if (i + 1 < line.length && line[i + 1] === '"') {
          field += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        field += c
      }
    } else {
      if (c === '"') {
        inQuotes = true
      } else if (c === ',') {
        fields.push(field)
        field = ''
      } else {
        field += c
      }
    }
  }
  fields.push(field)
  return fields
}

const handleSave = async () => {
  if (!formData.value.content || !formData.value.categoryName) {
    toast.warning('请填写句子内容和分类')
    return
  }
  if (formData.value.linkType === 'url') {
    const url = formData.value.linkUrl.trim()
    if (!url) {
      toast.warning('请输入跳转链接URL')
      return
    }
    if (!/^https?:\/\/.+/.test(url)) {
      toast.warning('请输入以 http:// 或 https:// 开头的有效URL')
      return
    }
  }
  if (formData.value.linkType === 'post' && !formData.value.postName) {
    toast.warning('请选择关联文章')
    return
  }
  saving.value = true
  try {
    const linkUrl = formData.value.linkType === 'url' ? formData.value.linkUrl.trim() : undefined
    const postName = formData.value.linkType === 'post' ? formData.value.postName : undefined
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
          linkUrl,
          postName,
        } as any,
        status: {...latestSentence.status, published: formData.value.published},
      }
      await sentenceCoreApiClient.sentence.updateSentence({
        name: editingSentenceName.value,
        sentence: updated,
      })
      toast.success('更新成功')
    } else {
      const sentence = buildSentence(
              formData.value.content,
              formData.value.categoryName,
              formData.value.author,
              formData.value.source,
              linkUrl,
              postName,
      )
      await batchCreate([sentence])
      toast.success('创建成功')
    }
    showFormModal.value = false
    await fetchSentencesSilently()
    await initCategories()
  } catch (e) {
    console.error('保存失败', e)
    toast.error(isEditing.value ? '更新失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

const handleBatchSave = async () => {
  if (!batchImportForm.value.categoryName) {
    toast.warning('请选择目标分类')
    return
  }

  if (batchImportMode.value === 'json' && parsedSentences.value.length === 0) {
    toast.warning('没有解析到有效的句子数据')
    return
  }
  if (batchImportMode.value === 'excel' && excelPreview.value.length === 0) {
    toast.warning('没有解析到有效的句子数据')
    return
  }
  if (batchImportMode.value === 'csv' && csvPreview.value.length === 0) {
    toast.warning('没有解析到有效的句子数据')
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
    } else if (batchImportMode.value === 'csv') {
      const sentenceList = csvPreview.value.map((item) =>
              buildSentence(item.content, batchImportForm.value.categoryName, item.author, item.source),
      )
      result = await batchCreate(sentenceList)
    } else {
      const sentenceList = parsedSentences.value.map((item) =>
              buildSentence(item.content, batchImportForm.value.categoryName, item.author, item.source),
      )
      result = await batchCreate(sentenceList)
    }
    toast.success(`导入完成！成功: ${result.success || 0}，失败: ${result.failed || 0}`)
    showBatchImportModal.value = false
    await fetchSentences()
    await initCategories()
  } catch (e) {
    console.error('批量导入失败', e)
    toast.error('批量导入失败')
  } finally {
    batchImporting.value = false
  }
}

const handleEdit = (sentence: Sentence) => {
  isEditing.value = true
  editingSentenceName.value = sentence.metadata.name
  editingOriginalSentence.value = sentence
  const spec = sentence.spec as any
  let linkType: 'none' | 'url' | 'post' = 'none'
  let linkUrl = ''
  let postName = ''
  postOptions.value = []
  if (spec.linkUrl) {
    linkType = 'url'
    linkUrl = spec.linkUrl
  } else if (spec.postName) {
    linkType = 'post'
    postName = spec.postName
    // 如果已加载全部文章，直接从中查找；否则异步获取单篇标题作为占位
    if (postsLoaded.value) {
      postOptions.value = allPosts.value
      const found = allPosts.value.find(p => p.name === postName)
      if (!found) {
        // 文章不在列表中（可能是已删除/未发布），仍然显示
        axiosInstance.get(`/apis/content.halo.run/v1alpha1/posts/${postName}`)
          .then(({ data }) => {
            const title = data.spec?.title || '(无标题)'
            postOptions.value = [...allPosts.value, { name: postName, title }]
          })
          .catch(() => {
            postOptions.value = [...allPosts.value, { name: postName, title: '(文章不存在)' }]
          })
      }
    } else {
      axiosInstance.get(`/apis/content.halo.run/v1alpha1/posts/${postName}`)
        .then(({ data }) => {
          const title = data.spec?.title || '(无标题)'
          postOptions.value = [{ name: postName, title }]
        })
        .catch(() => {
          postOptions.value = [{ name: postName, title: '(文章不存在)' }]
        })
    }
  } else if (postsLoaded.value) {
    // 无关联文章，但已加载全部列表，直接填充
    postOptions.value = allPosts.value
  }
  formData.value = {
    content: sentence.spec.content,
    categoryName: sentence.spec.categoryName,
    author: sentence.spec.author || '匿名',
    source: sentence.spec.source || '未知',
    published: sentence.status?.published ?? true,
    linkType,
    linkUrl,
    postName,
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
        toast.success('删除成功')
        await fetchSentencesSilently()
      } catch (e) {
        console.error('删除失败', e)
        toast.error('删除失败')
      }
    },
  })
}

/**
 * 批量操作完成后的统一收尾：
 * 1. 清空选择（避免脏数据） 2. 刷新句子列表 3. 刷新分类计数。
 */
const refreshAfterBatch = async () => {
  clearSelection()
  batchMoveCategory.value = ''
  await fetchSentences()
  await initCategories()
}

/**
 * 批量移动到分类。
 * 对每个选中项：先获取最新数据（防乐观锁），再更新 categoryName。
 * 使用并发 16（项目约定），单个失败不中断整体流程。
 */
const handleBatchMove = () => {
  if (!hasSelection.value) {
    toast.warning('请先选择要移动的句子')
    return
  }
  if (!batchMoveCategory.value) {
    toast.warning('请选择目标分类')
    return
  }
  const targetCategory = categories.value.find((c) => c.metadata.name === batchMoveCategory.value)
  const targetName = targetCategory?.spec.name || batchMoveCategory.value
  const count = selectedCount.value
  Dialog.warning({
    title: '批量移动确认',
    description: `确定将选中的 ${count} 条句子移动到分类「${targetName}」吗？`,
    confirmText: '移动',
    cancelText: '取消',
    onConfirm: () => executeBatch(
      'move',
      async () => {
        const targetCategoryName = batchMoveCategory.value
        const ids = [...selectedIdList.value]
        const tasks = ids.map((name) => async () => {
          const {data: latest} = await sentenceCoreApiClient.sentence.getSentence({name})
          const updated: Sentence = {
            ...latest,
            spec: {...latest.spec, categoryName: targetCategoryName},
          }
          await sentenceCoreApiClient.sentence.updateSentence({name, sentence: updated})
        })
        return {ids, results: await runWithConcurrency(tasks, 16)}
      },
      '移动',
    ),
  })
}

/**
 * 批量发布/取消发布。
 * 不弹确认框（操作可逆），直接执行。
 */
const handleBatchPublish = (published: boolean) => {
  if (!hasSelection.value) {
    toast.warning('请先选择要操作的句子')
    return
  }
  const count = selectedCount.value
  const action = published ? '发布' : '取消发布'
  Dialog.warning({
    title: `批量${action}确认`,
    description: `确定将选中的 ${count} 条句子${action}吗？`,
    confirmText: action,
    cancelText: '取消',
    onConfirm: () => executeBatch(
      published ? 'publish' : 'unpublish',
      async () => {
        const ids = [...selectedIdList.value]
        const tasks = ids.map((name) => async () => {
          const {data: latest} = await sentenceCoreApiClient.sentence.getSentence({name})
          const updated: Sentence = {
            ...latest,
            status: {...latest.status, published},
          }
          await sentenceCoreApiClient.sentence.updateSentence({name, sentence: updated})
        })
        return {ids, results: await runWithConcurrency(tasks, 16)}
      },
      action,
    ),
  })
}

/**
 * 批量删除（危险操作，二次确认）。
 * 单个失败不中断整体流程，失败项的选择 ID 会被清理。
 */
const handleBatchDelete = () => {
  if (!hasSelection.value) {
    toast.warning('请先选择要删除的句子')
    return
  }
  const count = selectedCount.value
  Dialog.warning({
    title: '批量删除确认',
    description: `确定要删除选中的 ${count} 条句子吗？该操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: () => executeBatch(
      'delete',
      async () => {
        const ids = [...selectedIdList.value]
        const tasks = ids.map((name) => async () => {
          await sentenceCoreApiClient.sentence.deleteSentence({name})
        })
        return {ids, results: await runWithConcurrency(tasks, 16)}
      },
      '删除',
    ),
  })
}

/**
 * 批量操作统一执行器。
 *
 * <p>错误处理策略（与 project_memory 约定一致）：
 * <ul>
 *   <li>单个项失败不中断整体流程（runWithConcurrency 已隔离）</li>
 *   <li>汇总成功/失败数，失败数 > 0 时使用 warning toast</li>
 *   <li>整体异常（如网络错误）使用 error toast</li>
 * </ul>
 *
 * @param type 操作类型（用于 loading 状态标记）
 * @param executor 实际执行函数，返回 {ids, results}
 * @param actionLabel 操作名称（用于 toast 提示）
 */
const executeBatch = async (
  type: 'move' | 'publish' | 'unpublish' | 'delete',
  executor: () => Promise<{
    ids: string[]
    results: PromiseSettledResult<unknown>[]
  }>,
  actionLabel: string,
) => {
  batchOperating.value = true
  batchOperatingType.value = type
  try {
    const {results} = await executor()
    const {success, failed} = summarizeBatchResult(results)
    if (failed === 0) {
      toast.success(`成功${actionLabel} ${success} 条句子`)
    } else if (success === 0) {
      toast.error(`${actionLabel}失败：${failed} 条全部失败`)
    } else {
      toast.warning(`${actionLabel}完成：成功 ${success} 条，失败 ${failed} 条`)
    }
    await refreshAfterBatch()
  } catch (e) {
    console.error(`批量${actionLabel}失败`, e)
    toast.error(`批量${actionLabel}失败`)
  } finally {
    batchOperating.value = false
    batchOperatingType.value = ''
  }
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

/* 批量操作工具栏（遵循 Halo 设计规范：与 sentence-list-toolbar 一致的灰白背景、标准边框） */
.sentence-batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #eaecf0;
  background: #f9fafb;
}

.batch-count {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
  user-select: none;
}

.batch-category-select {
  width: 160px;
}

/* 行内复选框 */
.sentence-row-checkbox {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-right: 4px;
  height: 100%;
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

  .sentence-batch-bar {
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

.form-field-wrapper {
  margin-bottom: 16px;
}

.form-field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 6px;
}

/* 导出分类多选列表 */
.export-category-list {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.export-category-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  transition: background-color 0.12s ease;
  border-bottom: 1px solid #f3f4f6;
}

.export-category-item:last-child {
  border-bottom: none;
}

.export-category-item:hover {
  background: #f9fafb;
}

.export-category-item input[type="checkbox"] {
  width: 15px;
  height: 15px;
  accent-color: #3b82f6;
  cursor: pointer;
  flex-shrink: 0;
}

.export-category-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.export-category-count {
  flex-shrink: 0;
  font-size: 12px;
  color: #9ca3af;
}

.export-category-item--all {
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.export-category-item--all .export-category-name {
  font-weight: 600;
  color: #111827;
}
</style>
