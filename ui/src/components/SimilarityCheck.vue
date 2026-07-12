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
              :class="{ active: viewMode === 'group' }"
              class="sim-tab"
              type="button"
              @click="viewMode = 'group'"
            >
              <el-icon :size="14"><Connection/></el-icon>
              分组
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
          <!-- 分组视图 -->
          <div v-if="viewMode === 'group'" key="group" class="sim-group-wrap">
            <div v-if="groups.length === 0 && !groupLoading" class="sim-empty">
              <el-icon :size="36" color="#10b981"><CircleCheckFilled/></el-icon>
              <p class="sim-empty-text">未发现相似句子，所有内容均为唯一</p>
            </div>
            <template v-else>
              <!-- 批量操作栏 -->
              <div v-if="groups.length > 0" class="sim-group-toolbar">
                <span class="sim-group-toolbar-info">
                  共 <strong>{{ groupTotal }}</strong> 组相似句子
                </span>
                <VButton
                  size="sm"
                  type="danger"
                  :loading="batchDeleting"
                  @click="handleBatchDeleteNonOptimal"
                >
                  <template #icon><el-icon :size="14"><Delete/></el-icon></template>
                  批量删除非最优句子
                </VButton>
              </div>
              <!-- 加载中 -->
              <div v-if="groupLoading" class="sim-loading">
                <el-icon :size="24" class="is-loading" color="#fb7185"><Loading/></el-icon>
                <p class="sim-loading-text">正在加载分组数据...</p>
              </div>
              <!-- 分组列表 -->
              <el-scrollbar v-else max-height="520px">
                <div class="sim-group-list">
                  <div v-for="(group, gi) in groups" :key="group.groupId" class="sim-group-card">
                    <!-- 最优句子 -->
                    <div class="sim-group-hub">
                      <span class="sim-group-num">{{ (groupPage - 1) * groupSize + gi + 1 }}</span>
                      <span class="sim-best-badge">最优</span>
                      <div class="sim-group-hub-body">
                        <div class="sim-group-hub-header">
                          <span class="sim-group-hub-text" :title="group.bestSentence.content">{{ group.bestSentence.content }}</span>
                          <span class="sim-group-hub-count">
                            <el-icon :size="12"><Connection/></el-icon>
                            关联 {{ group.similarCount }} 个句子
                          </span>
                        </div>
                        <div class="sim-group-hub-meta">
                          <VTag size="sm">作者：{{ group.bestSentence.author || '匿名' }}</VTag>
                          <VTag size="sm">分类：{{ getCategoryName(group.bestSentence.category) }}</VTag>
                          <VTag size="sm">来源：{{ group.bestSentence.source || '未知' }}</VTag>
                          <VTag size="sm" :class="group.bestSentence.published ? 'sim-tag-published' : ''">
                            {{ group.bestSentence.published ? '已发布' : '未发布' }}
                          </VTag>
                          <span class="sim-group-score">评分 {{ group.bestSentenceScore.toFixed(1) }}</span>
                        </div>
                      </div>
                      </div>
                      <!-- 相似句子列表 -->
                    <div class="sim-group-items">
                      <div
                        v-for="item in group.similarSentences"
                        :key="item.name"
                        class="sim-group-item"
                      >
                        <div class="sim-group-item-left">
                          <span class="sim-group-item-text" :title="item.content">{{ item.content }}</span>
                          <div class="sim-group-item-meta">
                            <VTag size="sm">作者：{{ item.author || '匿名' }}</VTag>
                            <VTag size="sm">分类：{{ getCategoryName(item.category) }}</VTag>
                            <VTag size="sm" :class="item.published ? 'sim-tag-published' : ''">
                              {{ item.published ? '已发布' : '未发布' }}
                            </VTag>
                            <span class="sim-group-score sim-group-score--sub">评分 {{ item.score.toFixed(1) }}</span>
                          </div>
                        </div>
                        <div class="sim-group-item-right">
                          <span class="sim-sim-pct" :class="getSimLevel(item.similarity)">
                            {{ (item.similarity * 100).toFixed(1) }}%
                          </span>
                          <div class="sim-group-item-bar">
                            <div
                              class="sim-sim-fill"
                              :class="getSimLevel(item.similarity)"
                              :style="{ width: `${item.similarity * 100}%` }"
                            ></div>
                          </div>
                          <button
                            v-tooltip="'删除'"
                            class="sim-act sim-act--del"
                            type="button"
                            :disabled="deletingSentence === item.name"
                            @click="handleDeleteSentence(item.name, item.content)"
                          >
                            <el-icon :size="13" :class="{ 'is-loading': deletingSentence === item.name }">
                              <Loading v-if="deletingSentence === item.name"/>
                              <Delete v-else/>
                            </el-icon>
                          </button>
                        </div>
                      </div>
                    </div>
                    <!-- 组统计 -->
                    <div class="sim-group-footer">
                      <span>最高相似度 <strong>{{ (group.maxSimilarity * 100).toFixed(1) }}%</strong></span>
                      <span>平均相似度 <strong>{{ (group.avgSimilarity * 100).toFixed(1) }}%</strong></span>
                    </div>
                  </div>
                </div>
              </el-scrollbar>
              <!-- 分页 -->
              <div class="sim-pagination">
                <el-config-provider :locale="zhCn">
                  <el-pagination
                    v-model:current-page="groupPage"
                    v-model:page-size="groupSize"
                    :total="groupTotal"
                    :page-sizes="[5, 10, 20]"
                    layout="total, prev, pager, next, sizes"
                    small
                    @update:current-page="onGroupPageChange"
                    @update:page-size="onGroupSizeChange"
                  />
                </el-config-provider>
              </div>
            </template>
          </div>

          <!-- 热力图视图 -->
          <div v-else key="heatmap" class="sim-heatmap-wrap">
            <div v-if="groups.length === 0" class="sim-empty">
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
  </div>
</template>

<script setup lang="ts">
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VTag,
} from '@halo-dev/components'
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Connection,
  Delete,
  Histogram,
  InfoFilled,
  Loading,
  Search,
} from '@element-plus/icons-vue'
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {axiosInstance} from '@halo-dev/api-client'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {HeatmapChart} from 'echarts/charts'
import {GridComponent, TooltipComponent, VisualMapComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import {categoryCoreApiClient, sentenceCoreApiClient} from '@/api'
import type {Category} from '@/api/generated'

use([HeatmapChart, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer])

// ============== 类型定义 ==============

interface SentenceInfo {
  name: string
  content: string
  category: string
  author: string
  source: string
  published: boolean
  likeCount: number
  viewCount: number
  score: number
  similarity: number
}

interface SimilarityGroup {
  groupId: string
  bestSentence: SentenceInfo
  bestSentenceScore: number
  similarSentences: SentenceInfo[]
  similarCount: number
  maxSimilarity: number
  avgSimilarity: number
}

interface GroupResult {
  page: number
  size: number
  total: number
  groups: SimilarityGroup[]
}

interface SimilarityCheckLog {
  apiVersion: string
  kind: string
  metadata: {
    name: string
    creationTimestamp: string
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
  }
}

interface SimilarityCheckLogList {
  page: number
  size: number
  total: number
  items: SimilarityCheckLog[]
}

// ============== 状态 ==============
const checkAlgorithm = ref('COSINE')
const checkThreshold = ref(0.8)
const triggering = ref(false)
const latestLog = ref<SimilarityCheckLog | null>(null)
const viewMode = ref<'group' | 'heatmap'>('group')

// 分组数据
const groups = ref<SimilarityGroup[]>([])
const groupPage = ref(1)
const groupSize = ref(5)
const groupTotal = ref(0)
const groupLoading = ref(false)
const batchDeleting = ref(false)

const categories = ref<Category[]>([])

const deletingSentence = ref<string | null>(null)

let checkPollTimer: ReturnType<typeof setTimeout> | null = null

// ==================== 计算属性 ====================
const isChecking = computed(() =>
  latestLog.value?.spec.status === 'RUNNING' || triggering.value,
)

const categoryMap = computed(() => {
  const map = new Map<string, string>()
  for (const c of categories.value) {
    map.set(c.metadata.name, c.spec.name)
  }
  return map
})

// ==================== 热力图 ====================
const HEATMAP_MAX = 30

const heatmapSentences = computed(() => {
  const seen = new Set<string>()
  const result: { name: string; content: string }[] = []
  for (const group of groups.value) {
    if (!seen.has(group.bestSentence.name)) {
      seen.add(group.bestSentence.name)
      result.push({name: group.bestSentence.name, content: group.bestSentence.content})
      if (result.length >= HEATMAP_MAX) break
    }
    for (const item of group.similarSentences) {
      if (!seen.has(item.name)) {
        seen.add(item.name)
        result.push({name: item.name, content: item.content})
        if (result.length >= HEATMAP_MAX) break
      }
    }
    if (result.length >= HEATMAP_MAX) break
  }
  return result
})

const heatmapOption = computed(() => {
  const sentences = heatmapSentences.value
  if (sentences.length === 0) return null

  // 从所有分组中构建相似度矩阵
  const simMap = new Map<string, number>()
  for (const group of groups.value) {
    const best = group.bestSentence
    for (const item of group.similarSentences) {
      simMap.set(`${best.name}|${item.name}`, item.similarity)
    }
  }

  const labels = sentences.map((_, i) => `#${i + 1}`)
  const data: [number, number, number][] = []

  for (let i = 0; i < sentences.length; i++) {
    for (let j = i; j < sentences.length; j++) {
      const key1 = `${sentences[i].name}|${sentences[j].name}`
      const key2 = `${sentences[j].name}|${sentences[i].name}`
      const sim = simMap.get(key1) ?? simMap.get(key2)
      data.push([i, j, sim ?? (i === j ? 1 : 0)])
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

// ==================== 数据获取 ====================

const BASE = '/apis/console.api.hitokotohub.puresky.top/v1alpha1'

const getCategoryName = (categoryId: string): string => {
  return categoryMap.value.get(categoryId) || categoryId || '-'
}

const initCategories = async () => {
  try {
    const {data} = await categoryCoreApiClient.category.listCategory({page: 1, size: 100})
    categories.value = data.items || []
  } catch (e) {
    console.error('获取分类列表失败', e)
  }
}

const fetchLatestLog = async (silent = false) => {
  try {
    const {data} = await axiosInstance.get<SimilarityCheckLogList>(
      `${BASE}/similarity-check-logs`,
      {params: {page: 1, size: 1}},
    )
    if (data.items?.length > 0) {
      latestLog.value = data.items[0]
    } else {
      latestLog.value = null
    }
  } catch (e) {
    if (!silent) console.error('获取检查日志失败', e)
  }
}

const fetchGroups = async () => {
  groupLoading.value = true
  try {
    const {data} = await axiosInstance.get<GroupResult>(
      `${BASE}/similarity-check-groups`,
      {params: {page: groupPage.value, size: groupSize.value}},
    )
    groups.value = data.groups || []
    groupTotal.value = data.total || 0
  } catch (e) {
    console.error('获取分组数据失败', e)
    groups.value = []
    groupTotal.value = 0
  } finally {
    groupLoading.value = false
  }
}

const onGroupPageChange = (newPage: number) => {
  groupPage.value = newPage
  fetchGroups()
}

const onGroupSizeChange = () => {
  groupPage.value = 1
  fetchGroups()
}

// ==================== 检查触发 ====================
const handleTriggerCheck = async () => {
  triggering.value = true
  try {
    await axiosInstance.post(
      `${BASE}/similarity-check-logs/-/trigger`,
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
  if (checkPollTimer) clearTimeout(checkPollTimer)
  let count = 0
  const poll = async () => {
    if (++count > 90) { stopCheckPolling(); return }
    await fetchLatestLog(true)
    if (latestLog.value && latestLog.value.spec.status !== 'RUNNING') {
      stopCheckPolling()
      // 检查完成后重新加载数据
      groupPage.value = 1
      await fetchGroups()
      return
    }
    // 递增间隔：前10次1s，之后3s
    const interval = count <= 10 ? 1000 : 3000
    checkPollTimer = setTimeout(poll, interval)
  }
  checkPollTimer = setTimeout(poll, 1000)
}

const stopCheckPolling = () => {
  if (checkPollTimer) { clearTimeout(checkPollTimer); checkPollTimer = null }
}

// ==================== 删除 ====================
const handleDeleteSentence = (name: string, content: string) => {
  if (deletingSentence.value) return  // 防重复点击
  Dialog.warning({
    title: '删除确认',
    description: `确定要删除「${content.slice(0, 30)}${content.length > 30 ? '...' : ''}」吗？此操作不可撤销。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      deletingSentence.value = name
      try {
        await sentenceCoreApiClient.sentence.deleteSentence({name})
        Toast.success('删除成功')
        // 乐观更新：直接从本地 groups 中移除该句子，保持排序不变
        removeSentenceFromGroups(name)
      } catch (e) {
        console.error('删除失败', e)
        Toast.error('删除失败')
      } finally {
        deletingSentence.value = null
      }
    },
  })
}

/**
 * 从本地 groups 中移除指定句子（乐观更新）
 * - 如果删除的是相似句子：从 similarSentences 中移除，更新 similarCount
 * - 如果删除的是最优句子：从相似句子中选评分最高的作为新最优
 * - 如果删除后组内不足 2 个句子，移除整个组
 */
const removeSentenceFromGroups = (name: string) => {
  const newGroups: SimilarityGroup[] = []
  for (const group of groups.value) {
    // 删除的是最优句子
    if (group.bestSentence.name === name) {
      if (group.similarSentences.length === 0) {
        // 组内没有其他句子，移除整个组
        groupTotal.value--
        continue
      }
      // 从相似句子中选评分最高的作为新最优
      const sorted = [...group.similarSentences].sort((a, b) => b.score - a.score)
      const newBest = sorted[0]
      const newSimilar = sorted.slice(1)
      newGroups.push({
        ...group,
        bestSentence: newBest,
        bestSentenceScore: newBest.score,
        similarSentences: newSimilar,
        similarCount: newSimilar.length,
      })
      continue
    }

    // 删除的是相似句子
    const idx = group.similarSentences.findIndex(s => s.name === name)
    if (idx >= 0) {
      const newSimilar = group.similarSentences.filter(s => s.name !== name)
      if (newSimilar.length === 0) {
        // 组内只剩最优句子，移除整个组
        groupTotal.value--
        continue
      }
      newGroups.push({
        ...group,
        similarSentences: newSimilar,
        similarCount: newSimilar.length,
      })
      continue
    }

    // 该组不包含被删除的句子，保持不变
    newGroups.push(group)
  }
  groups.value = newGroups
}

const handleBatchDeleteNonOptimal = () => {
  if (groupTotal.value === 0) {
    Toast.info('没有可删除的非最优句子')
    return
  }
  Dialog.warning({
    title: '批量删除确认',
    description: `将删除全部 ${groupTotal.value} 组相似中的非最优句子，仅保留每组的最优句子。此操作不可撤销，确定继续？`,
    confirmType: 'danger',
    confirmText: '全部删除',
    cancelText: '取消',
    onConfirm: async () => {
      batchDeleting.value = true
      try {
        const {data} = await axiosInstance.post<{message: string; deleted: number}>(
          `${BASE}/similarity-check-groups/-/delete-nonoptimal`,
        )
        Toast.success(data.message || '批量删除完成')
        // 重新加载数据
        groupPage.value = 1
        await fetchLatestLog()
        await fetchGroups()
      } catch (e: any) {
        Toast.error(e?.response?.data?.message || '批量删除失败')
      } finally {
        batchDeleting.value = false
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
  fetchLatestLog().then(() => {
    if (latestLog.value && latestLog.value.spec.status === 'SUCCESS') {
      fetchGroups()
    } else if (latestLog.value?.spec.status === 'RUNNING') {
      startCheckPolling()
    }
  })
})

onUnmounted(() => {
  stopCheckPolling()
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

/* ============================ 相似度条（sim-pct 和 sim-fill 在分组视图中复用） ============================ */
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
.sim-act--del:hover { background: #fef2f2; color: #ef4444; }

/* ============================ 分组视图 ============================ */
.sim-group-wrap {
  min-height: 160px;
}

.sim-group-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 12px;
  flex-wrap: wrap;
}

.sim-group-toolbar-info {
  font-size: 13px;
  color: #6b7280;
}

.sim-group-toolbar-info strong {
  color: #111827;
}

.sim-group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}

.sim-group-card {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
  transition: border-color 0.15s;
}

.sim-group-card:hover {
  border-color: #fda4af;
}

/* 最优句子 */
.sim-group-hub {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  background: #fdf2f4;
  border-bottom: 1px solid #fce7f3;
}

.sim-group-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fb7185;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 2px;
}

.sim-best-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border-radius: 4px;
  background: linear-gradient(135deg, #f43f5e, #e11d48);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  margin-top: 4px;
}

.sim-group-hub-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sim-group-hub-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sim-group-hub-text {
  font-size: 14px;
  font-weight: 600;
  color: #881337;
  line-height: 1.5;
  flex: 1;
  min-width: 0;
}

.sim-group-hub-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #fecdd3;
  color: #9f1239;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.sim-group-hub-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.sim-group-score {
  font-size: 11px;
  font-weight: 600;
  color: #e11d48;
  background: #fff1f2;
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.sim-group-score--sub {
  color: #9ca3af;
  background: #f9fafb;
}

/* 已发布标签 */
:deep(.sim-tag-published) {
  --tag-color: #059669;
  --tag-border-color: #a7f3d0;
  --tag-bg-color: #ecfdf5;
}

/* 相似项列表 */
.sim-group-items {
  display: flex;
  flex-direction: column;
}

.sim-group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #fafafa;
  transition: background-color 0.12s;
}

.sim-group-item:last-child {
  border-bottom: none;
}

.sim-group-item:hover {
  background: #fff5f5;
}

.sim-group-item-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sim-group-item-text {
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sim-group-item-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.sim-group-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.sim-group-item-bar {
  width: 60px;
  height: 4px;
  border-radius: 2px;
  background: #f0f0f0;
  overflow: hidden;
}

/* 组底部统计 */
.sim-group-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  background: #fafafa;
  border-top: 1px solid #f5f5f5;
  font-size: 12px;
  color: #9ca3af;
}

.sim-group-footer strong {
  color: #374151;
  font-weight: 600;
}

/* 分页 */
.sim-pagination {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
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

/* ============================ Element Plus 覆盖 ============================ */
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
