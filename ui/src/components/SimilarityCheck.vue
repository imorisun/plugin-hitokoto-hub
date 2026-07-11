<template>
  <div class="sim-page">
    <!-- ======================== 控制面板 ======================== -->
    <div class="sim-panel">
      <div class="sim-panel-header">
        <div class="sim-panel-title">
          <span class="sim-panel-title-text">句子相似度检查</span>
          <span v-if="latestLog" class="sim-status-pill" :class="`is-${latestLog.spec.status.toLowerCase()}`">
            <span class="sim-status-pill-dot"></span>
            {{ getStatusLabel(latestLog.spec.status) }}
          </span>
          <span v-if="syncing" class="sim-sync-pill">
            <el-icon :size="12" class="is-loading"><Loading/></el-icon>
            正在同步
          </span>
        </div>
        <p class="sim-panel-desc">对所有已保存的句子进行两两相似度比对，发现重复或高度相似的内容</p>
      </div>

      <div class="sim-panel-controls">
        <div class="sim-field">
          <label class="sim-field-label">算法</label>
          <el-select v-model="checkAlgorithm" size="default" class="sim-field-select">
            <el-option label="余弦相似度（TF-IDF）" value="COSINE"/>
            <el-option label="Jaccard 相似度" value="JACCARD"/>
          </el-select>
        </div>

        <div class="sim-field sim-field--grow">
          <label class="sim-field-label">
            相似度阈值
            <span class="sim-threshold-tag">{{ (checkThreshold * 100).toFixed(0) }}%</span>
          </label>
          <div class="sim-slider-wrap">
            <el-slider
              v-model="checkThreshold"
              :min="0.1"
              :max="1.0"
              :step="0.05"
              :format-tooltip="(val: number) => (val * 100).toFixed(0) + '%'"
            />
          </div>
        </div>

        <VButton
          type="primary"
          :loading="triggering"
          class="sim-trigger-btn"
          @click="handleTriggerCheck"
        >
          <template #icon>
            <el-icon><Search/></el-icon>
          </template>
          立即检查
        </VButton>
      </div>

      <!-- 检查进度 -->
      <Transition name="sim-collapse">
        <div v-if="isChecking" class="sim-progress">
          <div class="sim-progress-track"><div class="sim-progress-fill"></div></div>
          <span class="sim-progress-label">正在执行检查...</span>
        </div>
      </Transition>
    </div>

    <!-- ======================== 结果指标 ======================== -->
    <Transition name="sim-fade" v-if="latestLog">
      <div class="sim-stats">
        <div class="sim-stat">
          <span class="sim-stat-label">句子总数</span>
          <span class="sim-stat-value">{{ latestLog.spec.totalSentences }}</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat">
          <span class="sim-stat-label">比较对数</span>
          <span class="sim-stat-value">{{ latestLog.spec.totalPairs.toLocaleString() }}</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat">
          <span class="sim-stat-label">相似对数</span>
          <span
            class="sim-stat-value"
            :class="latestLog.spec.similarPairCount > 0 ? 'is-alert' : 'is-ok'"
          >{{ latestLog.spec.similarPairCount }}</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat">
          <span class="sim-stat-label">算法</span>
          <span class="sim-stat-value sim-stat-value--sm">{{ latestLog.spec.algorithm }}</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat">
          <span class="sim-stat-label">阈值</span>
          <span class="sim-stat-value sim-stat-value--sm">{{ (latestLog.spec.threshold * 100).toFixed(0) }}%</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat">
          <span class="sim-stat-label">耗时</span>
          <span class="sim-stat-value sim-stat-value--sm">{{ formatDuration(latestLog.spec.durationMs) }}</span>
        </div>
        <div class="sim-stat-divider"></div>
        <div class="sim-stat sim-stat--time">
          <span class="sim-stat-label">检查时间</span>
          <span class="sim-stat-value sim-stat-value--sm">{{ formatTime(latestLog.metadata.creationTimestamp) }}</span>
        </div>
      </div>
    </Transition>

    <!-- ======================== 结果展示 ======================== -->
    <VCard v-if="latestLog && latestLog.spec.status === 'SUCCESS'" :body-class="['!p-0']">
      <template #header>
        <div class="sim-section-header">
          <div class="sim-section-title-group">
            <span class="sim-section-title">检查结果</span>
            <span
              class="sim-result-badge"
              :class="latestLog.spec.similarPairCount > 0 ? 'is-alert' : 'is-ok'"
            >
              {{ latestLog.spec.similarPairCount > 0 ? `${latestLog.spec.similarPairCount} 组相似` : '无相似' }}
            </span>
          </div>
          <div class="sim-tabs">
            <button
              :class="{ active: viewMode === 'table' }"
              class="sim-tab"
              type="button"
              @click="viewMode = 'table'"
            >
              <el-icon :size="14"><Grid/></el-icon>
              列表
            </button>
            <button
              :class="{ active: viewMode === 'heatmap' }"
              class="sim-tab"
              type="button"
              @click="viewMode = 'heatmap'"
            >
              <el-icon :size="14"><Histogram/></el-icon>
              热力图
            </button>
          </div>
        </div>
      </template>

      <div class="sim-results">
        <Transition name="sim-slide" mode="out-in">
          <!-- 表格视图 -->
          <div v-if="viewMode === 'table'" key="table" class="sim-table-wrap">
            <div v-if="similarPairs.length === 0" class="sim-empty">
              <el-icon :size="36" color="#10b981"><CircleCheckFilled/></el-icon>
              <p class="sim-empty-text">未发现相似句子，所有内容均为唯一</p>
            </div>
            <el-scrollbar v-else max-height="520px">
              <el-table :data="similarPairs" style="width: 100%" table-layout="auto">
                <el-table-column label="#" width="48" type="index" align="center" />
                <el-table-column label="句子 A" min-width="300">
                  <template #default="{ row }">
                    <div class="sim-cell">
                      <span class="sim-cell-text" :title="row.sentence1Content">{{ row.sentence1Content }}</span>
                      <div class="sim-cell-meta">
                        <VTag>作者：{{ row.sentence1Author || '匿名' }}</VTag>
                         <VTag>分类：{{ getCategoryName(row.sentence1Category) }}</VTag>
                         <VTag>来源：{{ row.sentence1Source || '未知' }}</VTag>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="句子 B" min-width="300">
                  <template #default="{ row }">
                    <div class="sim-cell">
                      <span class="sim-cell-text" :title="row.sentence2Content">{{ row.sentence2Content }}</span>
                      <div class="sim-cell-meta">
                        <VTag>作者：{{ row.sentence2Author || '匿名' }}</VTag>
                         <VTag>分类：{{ getCategoryName(row.sentence2Category) }}</VTag>
                         <VTag>来源：{{ row.sentence2Source || '未知' }}</VTag>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="相似度" width="130" align="center">
                  <template #default="{ row }">
                    <div class="sim-sim">
                      <div class="sim-sim-bar">
                        <div
                          class="sim-sim-fill"
                          :class="getSimLevel(row.similarity)"
                          :style="{ width: `${row.similarity * 100}%` }"
                        ></div>
                      </div>
                      <span class="sim-sim-pct" :class="getSimLevel(row.similarity)">
                        {{ (row.similarity * 100).toFixed(1) }}%
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="110" fixed="right" align="center">
                  <template #default="{ row }">
                    <div class="sim-actions">
                      <button v-tooltip="'编辑 A'" class="sim-act sim-act--edit" type="button" @click="handleEditByPair(row, 1)">
                        <el-icon><EditPen/></el-icon><span class="sim-act-label">A</span>
                      </button>
                      <button v-tooltip="'编辑 B'" class="sim-act sim-act--edit" type="button" @click="handleEditByPair(row, 2)">
                        <el-icon><EditPen/></el-icon><span class="sim-act-label">B</span>
                      </button>
                      <button v-tooltip="'删除 A'" class="sim-act sim-act--del" type="button" @click="handleDeleteByPair(row, 1)">
                        <el-icon><Delete/></el-icon><span class="sim-act-label">A</span>
                      </button>
                      <button v-tooltip="'删除 B'" class="sim-act sim-act--del" type="button" @click="handleDeleteByPair(row, 2)">
                        <el-icon><Delete/></el-icon><span class="sim-act-label">B</span>
                      </button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </el-scrollbar>
            <div v-if="latestLog.spec.similarPairCount > similarPairs.length" class="sim-truncated">
              <el-icon :size="13"><InfoFilled/></el-icon>
              仅显示前 {{ similarPairs.length }} 条，共 {{ latestLog.spec.similarPairCount }} 条
            </div>
          </div>

          <!-- 热力图视图 -->
          <div v-else key="heatmap" class="sim-heatmap-wrap">
            <div v-if="similarPairs.length === 0" class="sim-empty">
              <el-icon :size="36" color="#10b981"><CircleCheckFilled/></el-icon>
              <p class="sim-empty-text">未发现相似句子，无需生成热力图</p>
            </div>
            <div v-else>
              <p class="sim-heatmap-hint">
                <el-icon :size="13"><InfoFilled/></el-icon>
                展示前 {{ heatmapSentences.length }} 个句子的关联矩阵，颜色越深相似度越高
              </p>
              <VChart
                v-if="heatmapOption"
                :option="heatmapOption"
                :style="{ height: '480px', width: '100%' }"
                autoresize
              />
            </div>
          </div>
        </Transition>
      </div>
    </VCard>

    <!-- 检查中 -->
    <VCard v-else-if="latestLog && latestLog.spec.status === 'RUNNING'" :body-class="['!p-0']">
      <div class="sim-loading">
        <el-icon :size="32" class="is-loading" color="#fb7185"><Loading/></el-icon>
        <p class="sim-loading-text">正在执行相似度检查...</p>
      </div>
    </VCard>

    <!-- 检查失败 -->
    <VCard v-else-if="latestLog && latestLog.spec.status === 'FAILED'" :body-class="['!p-0']">
      <div class="sim-loading">
        <el-icon :size="32" color="#ef4444"><CircleCloseFilled/></el-icon>
        <p class="sim-loading-text">检查失败：{{ latestLog.spec.errorMessage || '未知错误' }}</p>
        <VButton size="sm" type="secondary" class="sim-retry-btn" @click="handleTriggerCheck">重新检查</VButton>
      </div>
    </VCard>

    <!-- 无检查记录 -->
    <VCard v-else :body-class="['!p-0']">
      <div class="sim-empty-state">
        <el-icon :size="48" color="#d1d5db"><Search/></el-icon>
        <p class="sim-empty-state-title">暂无检查结果</p>
        <p class="sim-empty-state-desc">点击上方「立即检查」按钮，对所有句子进行相似度比对</p>
      </div>
    </VCard>

    <!-- ======================== 编辑弹窗 ======================== -->
    <VModal v-model:visible="showEditModal" title="编辑句子" :width="600">
      <div class="sim-form">
        <FormKit
          v-model="editForm.content"
          type="textarea"
          label="句子内容"
          validation="required"
          validation-message="请输入句子内容"
          placeholder="请输入句子内容"
          :rows="4"
        />
        <FormKit
          v-model="editForm.categoryName"
          type="select"
          label="分类"
          validation="required"
          validation-message="请选择分类"
          placeholder="请选择分类"
          :options="categorySelectOptions"
        />
        <FormKit
          v-model="editForm.author"
          type="text"
          label="作者"
          placeholder="请输入作者（默认为匿名）"
        />
        <FormKit
          v-model="editForm.source"
          type="text"
          label="来源"
          placeholder="请输入来源（默认为未知）"
        />
        <FormKit
          v-model="editForm.published"
          type="checkbox"
          label="发布状态"
          help="勾选后公开对外可见，未勾选则仅管理员可见"
        >
          已发布
        </FormKit>
      </div>
      <template #footer>
        <div class="sim-modal-footer">
          <VButton @click="showEditModal = false">取消</VButton>
          <VButton type="secondary" :loading="saving" @click="handleSaveSentence">保存修改</VButton>
        </div>
      </template>
    </VModal>
  </div>
</template>

<script setup lang="ts">
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VModal,
  VTag,
} from '@halo-dev/components'
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Delete,
  EditPen,
  Grid,
  Histogram,
  InfoFilled,
  Loading,
  Search,
} from '@element-plus/icons-vue'
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {axiosInstance} from '@halo-dev/api-client'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {HeatmapChart} from 'echarts/charts'
import {GridComponent, TooltipComponent, VisualMapComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import {categoryCoreApiClient, sentenceCoreApiClient} from '@/api'
import type {Category, Sentence} from '@/api/generated'

use([HeatmapChart, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer])

interface SimilarityPair {
  sentence1Name: string
  sentence1Content: string
  sentence1Category: string
  sentence1Author: string
  sentence1Source: string
  sentence2Name: string
  sentence2Content: string
  sentence2Category: string
  sentence2Author: string
  sentence2Source: string
  similarity: number
}

interface SimilarityCheckLog {
  apiVersion: string
  kind: string
  metadata: {
    name: string
    creationTimestamp: string
    deletionTimestamp?: string
  }
  spec: {
    triggerType: 'MANUAL' | 'SCHEDULED'
    triggeredBy: string
    algorithm: string
    threshold: number
    totalSentences: number
    totalPairs: number
    similarPairCount: number
    durationMs: number
    status: 'RUNNING' | 'SUCCESS' | 'FAILED'
    errorMessage?: string
    similarPairs: string
  }
}

interface SimilarityCheckLogList {
  page: number
  size: number
  total: number
  items: SimilarityCheckLog[]
}

// ==================== 状态 ====================
const checkAlgorithm = ref('COSINE')
const checkThreshold = ref(0.8)
const triggering = ref(false)
const syncing = ref(false)
const latestLog = ref<SimilarityCheckLog | null>(null)
const similarPairs = ref<SimilarityPair[]>([])
const viewMode = ref<'table' | 'heatmap'>('table')

const categories = ref<Category[]>([])

const showEditModal = ref(false)
const saving = ref(false)
const editingSentenceName = ref('')
const editForm = ref({
  content: '',
  categoryName: '',
  author: '匿名',
  source: '未知',
  published: true,
})

let checkPollTimer: ReturnType<typeof setInterval> | null = null
let syncDebounceTimer: ReturnType<typeof setTimeout> | null = null

// ==================== 计算属性 ====================
const isChecking = computed(() =>
  latestLog.value?.spec.status === 'RUNNING' || triggering.value,
)

const categorySelectOptions = computed(() =>
  categories.value.map((c) => ({
    label: c.spec.name,
    value: c.metadata.name,
  })),
)

const currentThreshold = computed(() =>
  latestLog.value?.spec.threshold ?? checkThreshold.value,
)

const currentAlgorithm = computed(() =>
  latestLog.value?.spec.algorithm ?? checkAlgorithm.value,
)

// ==================== 热力图 ====================
const HEATMAP_MAX = 30

const heatmapSentences = computed(() => {
  const seen = new Set<string>()
  const result: { name: string; content: string }[] = []
  for (const pair of similarPairs.value) {
    if (!seen.has(pair.sentence1Name)) {
      seen.add(pair.sentence1Name)
      result.push({name: pair.sentence1Name, content: pair.sentence1Content})
    }
    if (!seen.has(pair.sentence2Name)) {
      seen.add(pair.sentence2Name)
      result.push({name: pair.sentence2Name, content: pair.sentence2Content})
    }
    if (result.length >= HEATMAP_MAX) break
  }
  return result
})

const heatmapOption = computed(() => {
  const sentences = heatmapSentences.value
  if (sentences.length === 0) return null

  const labels = sentences.map((_, i) => `#${i + 1}`)
  const data: [number, number, number][] = []

  for (let i = 0; i < sentences.length; i++) {
    for (let j = i; j < sentences.length; j++) {
      const pair = similarPairs.value.find(
        (p) =>
          (p.sentence1Name === sentences[i].name && p.sentence2Name === sentences[j].name) ||
          (p.sentence1Name === sentences[j].name && p.sentence2Name === sentences[i].name),
      )
      data.push([i, j, pair ? pair.similarity : (i === j ? 1 : 0)])
    }
  }

  return {
    tooltip: {
      position: 'top',
      formatter: (params: any) => {
        const [i, j, val] = params.data
        const s1 = sentences[i]
        const s2 = sentences[j]
        return `<div style="max-width:400px">
          <div style="font-weight:bold;margin-bottom:4px">相似度: ${(val * 100).toFixed(1)}%</div>
          <div style="margin-bottom:2px">A: ${s1.content.slice(0, 40)}${s1.content.length > 40 ? '...' : ''}</div>
          <div>B: ${s2.content.slice(0, 40)}${s2.content.length > 40 ? '...' : ''}</div>
        </div>`
      },
    },
    grid: {top: 30, bottom: 80, left: 60, right: 30},
    xAxis: {type: 'category', data: labels, splitArea: {show: true}, axisLabel: {fontSize: 11}},
    yAxis: {type: 'category', data: labels, splitArea: {show: true}, axisLabel: {fontSize: 11}},
    visualMap: {
      min: 0, max: 1, calculable: true, orient: 'horizontal',
      left: 'center', bottom: 10,
      inRange: {color: ['#f0f9ff', '#fda4af', '#e11d48']},
      text: ['高', '低'],
    },
    series: [{
      type: 'heatmap', data,
      label: {show: false},
      emphasis: {itemStyle: {shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)'}},
    }],
  }
})

// ==================== 前端相似度算法 ====================
// 与后端保持一致的字符二元组分词

function tokenizeToSet(text: string): Set<string> {
  if (!text || text.length < 2) return new Set()
  const tokens = new Set<string>()
  for (let i = 0; i < text.length - 1; i++) {
    tokens.add(text.substring(i, i + 2))
  }
  return tokens
}

function computeTfVector(text: string): Map<string, number> {
  const tf = new Map<string, number>()
  if (!text || text.length < 2) return tf
  let total = 0
  for (let i = 0; i < text.length - 1; i++) {
    const bigram = text.substring(i, i + 2)
    tf.set(bigram, (tf.get(bigram) || 0) + 1)
    total++
  }
  if (total > 0) {
    for (const [key, val] of tf) {
      tf.set(key, val / total)
    }
  }
  return tf
}

function computeIdfMap(tokenSets: Set<string>[]): Map<string, number> {
  const docFreq = new Map<string, number>()
  const n = tokenSets.length
  for (const tokens of tokenSets) {
    for (const token of tokens) {
      docFreq.set(token, (docFreq.get(token) || 0) + 1)
    }
  }
  const idf = new Map<string, number>()
  for (const [key, freq] of docFreq) {
    idf.set(key, Math.log(n / (freq + 1)) + 1)
  }
  return idf
}

function computeTfidfVector(tf: Map<string, number>, idf: Map<string, number>): Map<string, number> {
  const tfidf = new Map<string, number>()
  for (const [key, tfVal] of tf) {
    const idfVal = idf.get(key)
    if (idfVal !== undefined) {
      tfidf.set(key, tfVal * idfVal)
    }
  }
  return tfidf
}

function vectorNorm(v: Map<string, number>): number {
  let sum = 0
  for (const val of v.values()) {
    sum += val * val
  }
  return Math.sqrt(sum)
}

function cosineSimilarity(v1: Map<string, number>, v2: Map<string, number>): number {
  if (v1.size === 0 || v2.size === 0) return 0
  const smaller = v1.size <= v2.size ? v1 : v2
  const larger = v1.size <= v2.size ? v2 : v1
  let dot = 0
  for (const [key, val] of smaller) {
    const val2 = larger.get(key)
    if (val2 !== undefined) {
      dot += val * val2
    }
  }
  const n1 = vectorNorm(v1)
  const n2 = vectorNorm(v2)
  if (n1 === 0 || n2 === 0) return 0
  return dot / (n1 * n2)
}

function jaccardSimilarity(set1: Set<string>, set2: Set<string>): number {
  if (set1.size === 0 && set2.size === 0) return 0
  let intersection = 0
  for (const token of set1) {
    if (set2.has(token)) intersection++
  }
  const union = set1.size + set2.size - intersection
  return union === 0 ? 0 : intersection / union
}

/**
 * 对编辑后的句子重新计算与其他句子的相似度（乐观更新）
 * 使用与检查日志相同的算法和阈值
 */
function recalculateSimilarityForSentence(
  sentenceName: string,
  newContent: string,
): SimilarityPair[] {
  const algorithm = currentAlgorithm.value
  const threshold = currentThreshold.value

  // 收集所有唯一句子内容作为语料库（用于 IDF 计算）
  const contentMap = new Map<string, string>() // name -> content
  for (const pair of similarPairs.value) {
    if (!contentMap.has(pair.sentence1Name)) {
      contentMap.set(pair.sentence1Name, pair.sentence1Content)
    }
    if (!contentMap.has(pair.sentence2Name)) {
      contentMap.set(pair.sentence2Name, pair.sentence2Content)
    }
  }

  // 更新编辑后的句子内容
  contentMap.set(sentenceName, newContent)

  // 预计算所有句子的特征
  const tfVectors = new Map<string, Map<string, number>>()
  const tokenSets = new Map<string, Set<string>>()
  for (const [name, content] of contentMap) {
    tfVectors.set(name, computeTfVector(content))
    tokenSets.set(name, tokenizeToSet(content))
  }

  // 计算 IDF
  const allTokenSets = Array.from(tokenSets.values())
  const idfMap = computeIdfMap(allTokenSets)

  // 计算 TF-IDF 向量
  const tfidfVectors = new Map<string, Map<string, number>>()
  for (const [name, tf] of tfVectors) {
    tfidfVectors.set(name, computeTfidfVector(tf, idfMap))
  }

  // 重新计算涉及编辑句子的所有配对
  const updatedPairs: SimilarityPair[] = []
  for (const pair of similarPairs.value) {
    let content1 = pair.sentence1Content
    let content2 = pair.sentence2Content

    if (pair.sentence1Name === sentenceName) {
      content1 = newContent
    }
    if (pair.sentence2Name === sentenceName) {
      content2 = newContent
    }

    // 不涉及编辑句子的配对保持原样
    if (pair.sentence1Name !== sentenceName && pair.sentence2Name !== sentenceName) {
      updatedPairs.push(pair)
      continue
    }

    // 重新计算相似度
    let similarity: number
    if (algorithm === 'JACCARD') {
      const set1 = tokenSets.get(pair.sentence1Name)!
      const set2 = tokenSets.get(pair.sentence2Name)!
      similarity = jaccardSimilarity(set1, set2)
    } else {
      const v1 = tfidfVectors.get(pair.sentence1Name)!
      const v2 = tfidfVectors.get(pair.sentence2Name)!
      similarity = cosineSimilarity(v1, v2)
    }

    // 仅保留超过阈值的配对
    if (similarity >= threshold) {
      updatedPairs.push({
        ...pair,
        sentence1Content: content1,
        sentence2Content: content2,
        similarity: Math.round(similarity * 10000) / 10000,
      })
    }
  }

  // 按相似度降序排序
  updatedPairs.sort((a, b) => b.similarity - a.similarity)
  return updatedPairs
}

// ==================== 数据获取 ====================
const getCategoryName = (categoryId: string): string => {
  if (!categoryId) return '-'
  const category = categories.value.find((c) => c.metadata.name === categoryId)
  return category?.spec.name || categoryId
}

const initCategories = async () => {
  try {
    const {data} = await categoryCoreApiClient.category.listCategory({page: 1, size: 100})
    categories.value = data.items || []
  } catch (e) {
    console.error('获取分类列表失败', e)
  }
}

const handleTriggerCheck = async () => {
  triggering.value = true
  try {
    await axiosInstance.post(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/similarity-check-logs/-/trigger',
      null,
      {params: {algorithm: checkAlgorithm.value, threshold: checkThreshold.value}},
    )
    Toast.success('检查任务已触发')
    await fetchLatestLog()
    startCheckPolling()
  } catch (e: any) {
    Toast.error(e?.response?.data?.message || '触发检查失败')
  } finally {
    triggering.value = false
  }
}

const startCheckPolling = () => {
  if (checkPollTimer) clearInterval(checkPollTimer)
  let count = 0
  checkPollTimer = setInterval(async () => {
    if (++count > 150) {
      stopCheckPolling()
      syncing.value = false
      return
    }
    await fetchLatestLog(true)
    if (latestLog.value && latestLog.value.spec.status !== 'RUNNING') {
      stopCheckPolling()
      syncing.value = false
    }
  }, 2000)
}

const stopCheckPolling = () => {
  if (checkPollTimer) { clearInterval(checkPollTimer); checkPollTimer = null }
}

const fetchLatestLog = async (silent = false) => {
  try {
    const {data} = await axiosInstance.get<SimilarityCheckLogList>(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/similarity-check-logs',
      {params: {page: 1, size: 1}},
    )
    if (data.items?.length > 0) {
      latestLog.value = data.items[0]
      similarPairs.value = latestLog.value.spec.similarPairs
        ? JSON.parse(latestLog.value.spec.similarPairs)
        : []
    } else {
      latestLog.value = null
      similarPairs.value = []
    }
  } catch (e) {
    if (!silent) console.error('获取检查日志失败', e)
  }
}

/**
 * 触发后端重新检查以同步数据（防抖）
 * 在用户完成删除/修改操作后，自动重新计算并替换本地结果
 */
const scheduleSync = () => {
  if (syncDebounceTimer) clearTimeout(syncDebounceTimer)
  syncing.value = true
  syncDebounceTimer = setTimeout(async () => {
    syncDebounceTimer = null
    try {
      await axiosInstance.post(
        '/apis/console.api.hitokotohub.puresky.top/v1alpha1/similarity-check-logs/-/trigger',
        null,
        {params: {algorithm: checkAlgorithm.value, threshold: checkThreshold.value}},
      )
      startCheckPolling()
    } catch (e: any) {
      console.error('同步检查失败', e)
      syncing.value = false
    }
  }, 1500)
}

// ==================== 句子编辑（乐观更新 + 后台同步） ====================
const handleEditByPair = async (pair: SimilarityPair, which: number) => {
  const name = which === 1 ? pair.sentence1Name : pair.sentence2Name
  try {
    const {data} = await sentenceCoreApiClient.sentence.getSentence({name})
    editingSentenceName.value = name
    editForm.value = {
      content: data.spec.content,
      categoryName: data.spec.categoryName,
      author: data.spec.author || '匿名',
      source: data.spec.source || '未知',
      published: data.status?.published ?? true,
    }
    showEditModal.value = true
  } catch (e) {
    console.error('获取句子失败', e)
    Toast.error('获取句子失败')
  }
}

const handleSaveSentence = async () => {
  if (!editForm.value.content || !editForm.value.categoryName) {
    Toast.warning('请填写句子内容和分类')
    return
  }
  saving.value = true
  try {
    const {data: latest} = await sentenceCoreApiClient.sentence.getSentence({
      name: editingSentenceName.value,
    })
    const updated: Sentence = {
      ...latest,
      spec: {
        ...latest.spec,
        content: editForm.value.content,
        categoryName: editForm.value.categoryName,
        author: editForm.value.author,
        source: editForm.value.source,
      },
      status: {...latest.status, published: editForm.value.published},
    }
    await sentenceCoreApiClient.sentence.updateSentence({
      name: editingSentenceName.value,
      sentence: updated,
    })

    // 乐观更新：立即本地重算该句子与其他句子的相似度
    const newPairs = recalculateSimilarityForSentence(
      editingSentenceName.value,
      editForm.value.content,
    )
    const oldCount = similarPairs.value.length
    similarPairs.value = newPairs

    if (latestLog.value) {
      const diff = newPairs.length - oldCount
      if (diff !== 0) {
        latestLog.value = {
          ...latestLog.value,
          spec: {
            ...latestLog.value.spec,
            similarPairCount: Math.max(0, latestLog.value.spec.similarPairCount + diff),
          },
        }
      }
    }

    showEditModal.value = false
    Toast.success('保存成功，正在同步检查结果...')

    // 后台触发重新检查，保证数据与后端一致
    scheduleSync()
  } catch (e) {
    console.error('保存失败', e)
    Toast.error('保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 句子删除（乐观更新 + 后台同步） ====================
const handleDeleteByPair = (pair: SimilarityPair, which: number) => {
  const name = which === 1 ? pair.sentence1Name : pair.sentence2Name
  const content = which === 1 ? pair.sentence1Content : pair.sentence2Content
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除「${content.slice(0, 30)}${content.length > 30 ? '...' : ''}」吗？此操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await sentenceCoreApiClient.sentence.deleteSentence({name})

        // 乐观更新：立即移除涉及该句子的所有配对
        const filtered = similarPairs.value.filter(
          (p) => p.sentence1Name !== name && p.sentence2Name !== name,
        )
        const removedCount = similarPairs.value.length - filtered.length
        similarPairs.value = filtered

        if (latestLog.value) {
          latestLog.value = {
            ...latestLog.value,
            spec: {
              ...latestLog.value.spec,
              similarPairCount: Math.max(0, latestLog.value.spec.similarPairCount - removedCount),
              totalSentences: Math.max(0, latestLog.value.spec.totalSentences - 1),
            },
          }
        }

        Toast.success('删除成功，正在同步检查结果...')

        // 后台触发重新检查，保证数据与后端一致
        scheduleSync()
      } catch (e) {
        console.error('删除失败', e)
        Toast.error('删除失败')
      }
    },
  })
}

// ==================== 格式化工具 ====================
const formatTime = (ts: string): string => {
  if (!ts) return '-'
  try {
    return new Date(ts).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  } catch { return ts }
}

const formatDuration = (ms: number): string => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const getStatusLabel = (status: string): string => {
  switch (status) {
    case 'RUNNING': return '进行中'
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    default: return status
  }
}

const getSimLevel = (sim: number): string => {
  if (sim >= 0.9) return 'level-high'
  if (sim >= 0.7) return 'level-mid'
  return 'level-low'
}

// ==================== 生命周期 ====================
onMounted(() => {
  initCategories()
  fetchLatestLog()
  if (latestLog.value?.spec.status === 'RUNNING') startCheckPolling()
})

onUnmounted(() => {
  stopCheckPolling()
  if (syncDebounceTimer) clearTimeout(syncDebounceTimer)
})
</script>

<style scoped>
/* ============================ 基础 ============================ */
.sim-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============================ 控制面板 ============================ */
.sim-panel {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 20px 24px;
}

.sim-panel-header {
  margin-bottom: 16px;
}

.sim-panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.sim-panel-title-text {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.sim-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.sim-status-pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.sim-status-pill.is-running { background: #fef3c7; color: #92400e; }
.sim-status-pill.is-running .sim-status-pill-dot { background: #f59e0b; animation: sim-pulse 1.5s ease-in-out infinite; }
.sim-status-pill.is-success { background: #d1fae5; color: #065f46; }
.sim-status-pill.is-success .sim-status-pill-dot { background: #10b981; }
.sim-status-pill.is-failed { background: #fee2e2; color: #991b1b; }
.sim-status-pill.is-failed .sim-status-pill-dot { background: #ef4444; }

/* 同步状态标签 */
.sim-sync-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  background: #eff6ff;
  color: #1d4ed8;
}

@keyframes sim-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  50% { opacity: 0.6; box-shadow: 0 0 0 5px rgba(245, 158, 11, 0); }
}

.sim-panel-desc {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
}

.sim-panel-controls {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.sim-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sim-field--grow {
  flex: 1 1 200px;
  max-width: 280px;
}

.sim-field-label {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 6px;
}

.sim-threshold-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fb7185;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.sim-field-select {
  width: 200px;
}

.sim-slider-wrap {
  padding: 0 4px;
}

.sim-trigger-btn {
  margin-left: auto;
  border-radius: 8px;
}

/* 进度条 */
.sim-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff5f5;
}

.sim-progress-track {
  flex: 1;
  height: 3px;
  border-radius: 2px;
  background: #fce7f3;
  overflow: hidden;
  position: relative;
}

.sim-progress-fill {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, #fb7185, #f43f5e, #fb7185);
  background-size: 200% 100%;
  animation: sim-shimmer 1.5s linear infinite;
  border-radius: 2px;
}

@keyframes sim-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.sim-progress-label {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

/* ============================ 指标栏 ============================ */
.sim-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 14px 24px;
}

.sim-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 20px;
}

.sim-stat:first-child {
  padding-left: 0;
}

.sim-stat-divider {
  width: 1px;
  height: 28px;
  background: #f0f0f0;
}

.sim-stat-label {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
}

.sim-stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.sim-stat-value--sm {
  font-size: 13px;
  font-weight: 600;
}

.sim-stat-value.is-alert { color: #e11d48; }
.sim-stat-value.is-ok { color: #10b981; }

.sim-stat--time {
  margin-left: auto;
}

/* ============================ 卡片头 ============================ */
.sim-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  padding: 14px 16px;
}

.sim-section-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sim-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.sim-result-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.sim-result-badge.is-alert { background: #fef2f2; color: #e11d48; }
.sim-result-badge.is-ok { background: #ecfdf5; color: #10b981; }

/* ============================ Tab 切换 ============================ */
.sim-tabs {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: #f5f5f5;
  gap: 2px;
}

.sim-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  border-radius: 6px;
  transition: all 0.15s;
  cursor: pointer;
  border: none;
  background: transparent;
}

.sim-tab:hover { color: #374151; }

.sim-tab.active {
  background: #fff;
  color: #fb7185;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* ============================ 结果区域 ============================ */
.sim-results {
  padding: 0 16px 16px;
}

.sim-table-wrap,
.sim-heatmap-wrap {
  min-height: 160px;
}

/* ============================ 空状态 ============================ */
.sim-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
}

.sim-empty-text {
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
}

/* 无检查记录的引导空状态 */
.sim-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
}

.sim-empty-state-title {
  margin-top: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #374151;
}

.sim-empty-state-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #9ca3af;
}

/* ============================ 表格单元格 ============================ */
.sim-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 0;
}

.sim-cell-text {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: default;
}

.sim-cell-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

/* ============================ 相似度条 ============================ */
.sim-sim {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  width: 100%;
}

.sim-sim-bar {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: #f0f0f0;
  overflow: hidden;
}

.sim-sim-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.sim-sim-fill.level-high { background: #e11d48; }
.sim-sim-fill.level-mid { background: #f59e0b; }
.sim-sim-fill.level-low { background: #60a5fa; }

.sim-sim-pct {
  font-size: 12px;
  font-weight: 600;
}

.sim-sim-pct.level-high { color: #e11d48; }
.sim-sim-pct.level-mid { color: #d97706; }
.sim-sim-pct.level-low { color: #2563eb; }

/* ============================ 操作按钮 ============================ */
.sim-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.sim-act {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px 5px;
  border-radius: 5px;
  color: #9ca3af;
  transition: all 0.15s;
  cursor: pointer;
  border: none;
  background: transparent;
  font-size: 12px;
}

.sim-act:hover { background: #f5f5f5; }
.sim-act--edit:hover { background: #eff6ff; color: #2563eb; }
.sim-act--del:hover { background: #fef2f2; color: #ef4444; }

.sim-act-label {
  font-size: 10px;
  font-weight: 700;
  opacity: 0.6;
}

/* ============================ 热力图 ============================ */
.sim-heatmap-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #9ca3af;
}

/* ============================ 截断提示 ============================ */
.sim-truncated {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 12px;
  margin-top: 10px;
  font-size: 12px;
  color: #92400e;
  background: #fffbeb;
  border-radius: 6px;
}

/* ============================ 加载/失败 ============================ */
.sim-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 20px;
}

.sim-loading-text {
  margin-top: 12px;
  font-size: 14px;
  color: #6b7280;
}

.sim-retry-btn {
  margin-top: 16px;
}

/* ============================ 弹窗 ============================ */
.sim-form { padding: 0 4px; }

.sim-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ============================ Element Plus 覆盖 ============================ */
:deep(.el-table) {
  border: none !important;
  --el-table-border-color: #f5f5f5;
}

:deep(.el-table th.el-table__cell) {
  background-color: #fafafa !important;
  border-bottom: 1px solid #f0f0f0 !important;
  padding: 10px 12px;
  font-weight: 600;
  color: #374151;
  font-size: 13px;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #f5f5f5 !important;
  padding: 10px 12px;
}

:deep(.el-table__body tr) {
  transition: background-color 0.15s;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: #fdf2f4 !important;
}

:deep(.el-slider__runway) {
  margin: 10px 0;
  height: 4px;
  background: #fce7f3;
}

:deep(.el-slider__bar) {
  background: #fb7185;
  height: 4px;
}

:deep(.el-slider__button) {
  border-color: #fb7185;
  width: 14px;
  height: 14px;
}

:deep(.el-select__wrapper) {
  border-radius: 8px;
}

/* ============================ 过渡 ============================ */
.sim-fade-enter-active { transition: all 0.3s ease; }
.sim-fade-enter-from { opacity: 0; transform: translateY(8px); }

.sim-collapse-enter-active,
.sim-collapse-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.sim-collapse-enter-from,
.sim-collapse-leave-to { opacity: 0; max-height: 0; margin-top: 0 !important; }
.sim-collapse-enter-to,
.sim-collapse-leave-from { opacity: 1; max-height: 50px; }

.sim-slide-enter-active,
.sim-slide-leave-active { transition: all 0.2s ease; }
.sim-slide-enter-from { opacity: 0; transform: translateX(10px); }
.sim-slide-leave-to { opacity: 0; transform: translateX(-10px); }

/* ============================ 响应式 ============================ */
@media (max-width: 1024px) {
  .sim-stat--time {
    margin-left: 0;
    padding-left: 0;
  }

  .sim-stat--time::before {
    display: none;
  }
}

@media (max-width: 768px) {
  .sim-panel {
    padding: 16px;
  }

  .sim-panel-controls {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .sim-field-select,
  .sim-field--grow {
    width: 100%;
    max-width: none;
  }

  .sim-trigger-btn {
    margin-left: 0;
    width: 100%;
  }

  .sim-stats {
    padding: 12px 16px;
    gap: 8px;
  }

  .sim-stat {
    padding: 0 10px;
  }

  .sim-stat-divider {
    display: none;
  }

  .sim-stat-value {
    font-size: 15px;
  }

  .sim-section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .sim-tabs {
    width: 100%;
  }

  .sim-tab {
    flex: 1;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .sim-panel-title-text {
    font-size: 15px;
  }

  .sim-stats {
    flex-wrap: wrap;
  }

  .sim-stat {
    flex: 1 1 40%;
    padding: 6px 8px;
  }
}
</style>
