<template>
  <VCard>
    <div class="space-y-6">
      <!-- 概览卡片 -->
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
        <el-card class="custom-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">句子总数</p>
              <p class="mt-1 text-3xl font-bold text-slate-900">
                {{ statsData.sentenceCount.toLocaleString() }}</p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-blue-100 rounded-full">
              <el-icon :size="24" color="#3b82f6">
                <Document/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">分类数量</p>
              <p class="mt-1 text-3xl font-bold text-slate-900">{{ statsData.categoryCount }}</p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-purple-100 rounded-full">
              <el-icon :size="24" color="#8b5cf6">
                <PriceTag/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">已发布句子</p>
              <div class="flex items-baseline gap-2 mt-1">
                <p class="text-3xl font-bold text-green-600">
                  {{ statsData.publishedSentenceCount.toLocaleString() }}</p>
                <p class="text-sm text-slate-500">({{ publishedPercentage }}%)</p>
              </div>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-green-100 rounded-full">
              <el-icon :size="24" color="#10b981">
                <CircleCheck/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">未发布句子</p>
              <div class="flex items-baseline gap-2 mt-1">
                <p class="text-3xl font-bold text-red-600">
                  {{ statsData.notPublishedSentenceCount.toLocaleString() }}</p>
                <p class="text-sm text-slate-500">({{ notPublishedPercentage }}%)</p>
              </div>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-red-100 rounded-full">
              <el-icon :size="24" color="#ef4444">
                <CircleClose/>
              </el-icon>
            </div>
          </div>
        </el-card>
        <el-card class="custom-card stat-clickable" @click="handleStatClick('totalView')">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">总浏览量</p>
              <p class="mt-1 text-3xl font-bold text-slate-900">
                {{ viewStatsData.totalViewCount?.toLocaleString() || 0 }}
              </p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-orange-100 rounded-full">
              <el-icon :size="24" color="#f97316">
                <View/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card stat-clickable" @click="handleStatClick('todayView')">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">今日浏览量</p>
              <p class="mt-1 text-3xl font-bold text-indigo-600">
                {{ viewStatsData.todayViewCount?.toLocaleString() || 0 }}
              </p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-indigo-100 rounded-full">
              <el-icon :size="24" color="#6366f1">
                <Clock/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card stat-clickable" @click="handleStatClick('totalLike')">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">总点赞量</p>
              <p class="mt-1 text-3xl font-bold text-rose-500">
                {{ likeStatsData.totalLikeCount?.toLocaleString() || 0 }}
              </p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-rose-100 rounded-full">
              <el-icon :size="24" color="#f43f5e">
                <Star/>
              </el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="custom-card stat-clickable" @click="handleStatClick('todayLike')">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-slate-500">今日点赞量</p>
              <p class="mt-1 text-3xl font-bold text-pink-600">
                {{ likeStatsData.todayLikeCount?.toLocaleString() || 0 }}
              </p>
            </div>
            <div class="flex items-center justify-center w-12 h-12 bg-pink-100 rounded-full">
              <IconLike class="w-6 h-6 text-pink-600"/>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 图表区域 -->
      <div class="grid grid-cols-1 gap-6 charts-grid">
        <el-card class="custom-card">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold text-slate-900">分类句子分布</span>
              <el-tag type="info" size="small">{{ statsData.categoryCount }} 个分类</el-tag>
            </div>
          </template>
          <v-chart :option="categoryChartOption" style="height: 320px; width: 100%;" autoresize/>
        </el-card>

        <el-card class="custom-card">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold text-slate-900">句子状态统计</span>
              <el-tag type="info" size="small">共 {{ statsData.sentenceCount.toLocaleString() }}
                条
              </el-tag>
            </div>
          </template>
          <v-chart :option="statusChartOption" style="height: 320px; width: 100%;" autoresize/>
        </el-card>
      </div>

      <!-- 浏览量趋势折线图 -->
      <el-card class="custom-card">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-semibold text-slate-900">分类数据趋势</span>
            <div class="flex items-center gap-2">
              <el-select v-model="eventType" size="small" @change="fetchViewStats" style="width: 100px;">
                <el-option label="浏览量" value="VIEW" />
                <el-option label="点赞数" value="LIKE" />
              </el-select>
              <el-radio-group v-model="viewStatsDays" size="small" @change="fetchViewStats">
                <el-radio-button :value="7">7天</el-radio-button>
                <el-radio-button :value="30">30天</el-radio-button>
                <el-radio-button :value="90">90天</el-radio-button>
              </el-radio-group>
              <el-radio-group v-model="granularity" size="small" @change="fetchViewStats">
                <el-radio-button value="day">按天</el-radio-button>
                <el-radio-button value="week">按周</el-radio-button>
                <el-radio-button value="month">按月</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </template>
        <div v-loading="viewStatsLoading" style="min-height: 320px;">
          <v-chart
                  v-if="viewStatsData.echartsData && viewStatsData.echartsData.series?.length > 0"
                  :option="lineChartOption"
                  style="height: 320px; width: 100%;"
                  autoresize
          />
          <div v-else class="flex items-center justify-center" style="height: 320px;">
            <el-empty description="暂无浏览量数据" />
          </div>
        </div>
      </el-card>

      <!-- 分类详情表格 -->
      <el-card class="custom-card">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-semibold text-slate-900">分类详情</span>
            <el-tag type="info" size="small">{{ statsData.categoryCount }} 个分类</el-tag>
          </div>
        </template>
        <el-table
                :data="statsData.categoryDistribution"
                style="width: 100%"
                :stripe="true"
                table-layout="auto"
        >
          <el-table-column prop="displayName" label="分类名称"/>
          <el-table-column label="句子总数" min-width="100">
            <template #default="{ row }">
              <span class="font-medium">{{ row.count }}</span>
            </template>
          </el-table-column>
          <el-table-column label="浏览量">
            <template #default="{ row }">
              <span class="font-medium text-green-600">{{ row.viewCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="点赞量">
            <template #default="{ row }">
              <span class="font-medium text-rose-500">{{ row.likeCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已发布">
            <template #default="{ row }">
              <span class="font-medium text-green-600">{{ row.publishedCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="未发布">
            <template #default="{ row }">
              <span class="font-medium text-red-600">{{ row.notPublishedCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发布比例">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <el-progress
                        :percentage="row.count > 0 ? Math.round((row.publishedCount / row.count) * 100) : 0"
                        :stroke-width="10"
                        :color="getProgressColor(row.publishedCount, row.count)"
                        :show-text="false"
                />
                <span class="w-12 text-sm text-right text-slate-600 shrink-0">
                  {{ row.count > 0 ? Math.round((row.publishedCount / row.count) * 100) : 0 }}%
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 数据指标详情弹窗 -->
    <VModal
      v-model:visible="showDetailModal"
      :title="detailTitle"
      :width="720"
    >
      <div v-loading="detailLoading" class="detail-modal-body">
        <template v-if="detailData">
          <!-- 概要指标（可点击切换视图） -->
          <div class="detail-summary-grid">
            <div
              v-for="card in detailSummaryCards"
              :key="card.key"
              class="detail-summary-card"
              :class="{ 'is-active': card.active, 'is-clickable': card.clickable }"
              :style="card.active ? { borderColor: card.accent } : {}"
              @click="card.clickable && handleSummaryCardClick(card.metric)"
            >
              <span class="detail-summary-label">{{ card.label }}</span>
              <span
                class="detail-summary-value"
                :style="card.active ? { color: card.accent } : {}"
              >
                {{ card.value.toLocaleString() }}
              </span>
            </div>
          </div>

          <!-- 今日浏览/点赞 → 句子详情列表 -->
          <template v-if="detailMetricType === 'todayLike' || detailMetricType === 'todayView'">
            <div v-if="todaySentenceDetails.length > 0" class="detail-sentence-section">
              <div class="detail-section-title">
                <span>{{ detailMetricType === 'todayLike' ? '今日点赞句子' : '今日浏览句子' }}</span>
                <el-tag size="small" :type="detailMetricType === 'todayLike' ? 'danger' : ''">
                  共 {{ todaySentenceDetails.length }} 条
                </el-tag>
              </div>
              <div class="detail-sentence-list">
                <div
                  v-for="(item, idx) in todaySentenceDetails"
                  :key="item.sentenceName"
                  class="detail-sentence-item"
                >
                  <span class="detail-breakdown-rank" :class="idx === 0 ? 'is-first' : idx < 3 ? 'is-top3' : ''">
                    {{ idx + 1 }}
                  </span>
                  <div class="detail-sentence-body">
                    <p class="detail-sentence-content" :title="item.content">{{ item.content }}</p>
                    <div class="detail-sentence-meta">
                      <span>作者：{{ item.author || '匿名' }}</span>
                      <span class="detail-sentence-sep">|</span>
                      <span>分类：{{ item.categoryDisplayName || item.categoryName }}</span>
                      <span class="detail-sentence-sep">|</span>
                      <span>来源：{{ item.source || '未知' }}</span>
                    </div>
                  </div>
                  <div
                    class="detail-sentence-stats"
                    :class="detailMetricType === 'todayLike' ? 'is-like' : 'is-view'"
                  >
                    <span class="detail-sentence-event-count">{{ item.eventCount }}</span>
                    <span class="detail-sentence-event-label">
                      {{ detailMetricType === 'todayLike' ? '次点赞' : '次浏览' }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="detail-empty-chart">
              <el-empty
                :description="detailMetricType === 'todayLike' ? '今日暂无点赞数据' : '今日暂无浏览数据'"
                :image-size="60"
              />
            </div>
          </template>

          <!-- 总量 → 趋势图 + 分类占比 -->
          <template v-else>
            <!-- 趋势折线图 -->
            <div v-if="detailChartOption" class="detail-chart-section">
              <div class="detail-section-title">
                <span>近30天趋势</span>
                <el-tag size="small" type="info">{{ detailMetricType.includes('Like') ? '点赞' : '浏览' }}</el-tag>
              </div>
              <v-chart
                :option="detailChartOption"
                style="height: 280px; width: 100%;"
                autoresize
              />
            </div>
            <div v-else class="detail-empty-chart">
              <el-empty description="暂无趋势数据" :image-size="60"/>
            </div>

            <!-- 分类占比 -->
            <div class="detail-breakdown-section">
              <div class="detail-section-title">
                <span>分类占比（近30天）</span>
                <el-tag size="small" type="info">{{ detailCategoryBreakdown.length }} 个分类</el-tag>
              </div>
              <div v-if="detailCategoryBreakdown.length > 0" class="detail-breakdown-list">
                <div
                  v-for="(item, idx) in detailCategoryBreakdown"
                  :key="item.categoryName"
                  class="detail-breakdown-row"
                >
                  <span class="detail-breakdown-rank">{{ idx + 1 }}</span>
                  <span class="detail-breakdown-name" :title="item.displayName">{{ item.displayName }}</span>
                  <div class="detail-breakdown-bar-wrap">
                    <div
                      class="detail-breakdown-bar"
                      :style="{
                        width: detailCategoryTotal > 0
                          ? `${(item.count / detailCategoryTotal) * 100}%` : '0%',
                        background: metricConfig[detailMetricType].accent
                      }"
                    ></div>
                  </div>
                  <span class="detail-breakdown-count">{{ item.count.toLocaleString() }}</span>
                  <span class="detail-breakdown-pct">
                    {{ detailCategoryTotal > 0
                      ? ((item.count / detailCategoryTotal) * 100).toFixed(1) : '0.0' }}%
                  </span>
                </div>
              </div>
              <div v-else class="detail-empty-chart">
                <el-empty description="暂无分类数据" :image-size="60"/>
              </div>
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <div class="detail-modal-footer">
          <VButton @click="showDetailModal = false">关闭</VButton>
        </div>
      </template>
    </VModal>
  </VCard>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {
  CircleCheck,
  CircleClose,
  Clock,
  Document,
  PriceTag,
  Star,
  View
} from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import {VButton, VCard, VModal} from "@halo-dev/components"
import {overviewV1alpha1ApiClient} from "@/api"
import IconLike from '~icons/my-icons/like';
import {axiosInstance} from "@halo-dev/api-client";
import {useToast} from "@/composables/useToast"

const toast = useToast()

// 注册 ECharts 组件
use([PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const statsData = ref({
  categoryCount: 0,
  categoryDistribution: [
    {
      categoryName: "category-b6zywbf6",
      count: 0,
      displayName: "默认分类",
      notPublishedCount: 0,
      publishedCount: 0
    }
  ],
  notPublishedSentenceCount: 0,
  publishedSentenceCount: 0,
  sentenceCount: 0
})

// 浏览量统计数据
const viewStatsData = ref({
  success: false,
  totalViewCount: 0,
  todayViewCount: 0,
  viewTimeSeries: [],
  echartsData: {
    xAxis: [],
    series: []
  }
})

// 点赞量统计数据
const likeStatsData = ref({
  totalLikeCount: 0,
  todayLikeCount: 0
})

const viewStatsLoading = ref(false)
const viewStatsDays = ref(30)
const granularity = ref('day')
const eventType = ref('VIEW')

const publishedPercentage = computed(() => {
  if (statsData.value.sentenceCount === 0) return 0
  return Math.round((statsData.value.publishedSentenceCount / statsData.value.sentenceCount) * 100)
})

const notPublishedPercentage = computed(() => {
  if (statsData.value.sentenceCount === 0) return 0
  return Math.round((statsData.value.notPublishedSentenceCount / statsData.value.sentenceCount) * 100)
})

const getProgressColor = (published: number, total: number) => {
  if (total === 0) return '#ef4444'
  const ratio = published / total
  if (ratio >= 0.8) return '#10b981'
  if (ratio >= 0.5) return '#f59e0b'
  return '#ef4444'
}

const categoryChartOption = computed(() => {
  const categories = statsData.value.categoryDistribution
  const names = categories.map(c => c.displayName)
  const publishedData = categories.map(c => c.publishedCount)
  const notPublishedData = categories.map(c => c.notPublishedCount)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {type: 'shadow'}
    },
    legend: {
      data: ['已发布', '未发布'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        rotate: names.length > 5 ? 30 : 0,
        interval: 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '已发布',
        type: 'bar',
        stack: 'total',
        data: publishedData,
        itemStyle: {color: '#10b981'}
      },
      {
        name: '未发布',
        type: 'bar',
        stack: 'total',
        data: notPublishedData,
        itemStyle: {color: '#ef4444', borderRadius: [4, 4, 0, 0]}
      }
    ]
  }
})

const statusChartOption = computed(() => {
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 'center'
    },
    series: [
      {
        name: '句子状态',
        type: 'pie',
        radius: ['45%', '75%'],
        center: ['60%', '50%'],
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 3
        },
        label: {
          show: true,
          position: 'center',
          formatter: () => `{total|${statsData.value.sentenceCount.toLocaleString()}}\n{label|总计}`,
          rich: {
            total: {
              fontSize: 28,
              fontWeight: 'bold',
              color: '#1e293b'
            },
            label: {
              fontSize: 14,
              color: '#64748b',
              padding: [5, 0, 0, 0]
            }
          }
        },
        labelLine: {
          show: false
        },
        data: [
          {
            value: statsData.value.publishedSentenceCount,
            name: '已发布',
            itemStyle: {color: '#10b981'}
          },
          {
            value: statsData.value.notPublishedSentenceCount,
            name: '未发布',
            itemStyle: {color: '#ef4444'}
          }
        ]
      }
    ]
  }
})

// 折线图配置
const lineChartOption = computed(() => {
  const echartsData = viewStatsData.value.echartsData
  if (!echartsData || !echartsData.series || echartsData.series.length === 0) {
    return {}
  }

  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16']

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {type: 'shadow'}
    },
    legend: {
      data: echartsData.series.map((s: any) => s.displayName || s.name),
      top: 0,
      right: 0,
      type: 'scroll'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: echartsData.xAxis,
      name: '日期',
      axisLabel: {
        rotate: echartsData.xAxis.length > 10 ? 30 : 0,
        interval: 0
      }
    },
    yAxis: {
      type: 'value',
      name: '数据量'
    },
    series: echartsData.series.map((s: any, index: number) => ({
      name: s.displayName || s.name,
      type: 'line',
      data: s.data,
      smooth: s.smooth,
      lineStyle: {
        width: 2,
        color: colors[index % colors.length]
      },
      symbol: 'circle',
      symbolSize: 6,
      areaStyle: {
        opacity: 0.1,
        color: colors[index % colors.length]
      }
    }))
  }
})

onMounted(() => {
  overviewV1alpha1ApiClient.overview.getOverview().then(response => {
    statsData.value = response.data as any
  })
  fetchViewStats()
  fetchLikeStats()
})

// 获取点赞量统计（总点赞量、今日点赞量）
const fetchLikeStats = async () => {
  try {
    const response = await overviewV1alpha1ApiClient.overview.getViewStatistics({
      params: {
        days: 1,
        granularity: 'day',
        eventType: 'LIKE'
      }
    })
    const data = response.data as any
    likeStatsData.value = {
      totalLikeCount: data.totalViewCount || 0,
      todayLikeCount: data.todayViewCount || 0
    }
  } catch (error) {
    console.error('获取点赞量统计数据失败:', error)
  }
}

// 获取浏览量统计数据
const fetchViewStats = async () => {
  viewStatsLoading.value = true
  try {
    const response = await overviewV1alpha1ApiClient.overview.getViewStatistics({
      params: {
        days: viewStatsDays.value,
        granularity: granularity.value,
        eventType: eventType.value
      }
    })
    viewStatsData.value = response.data as any
  } catch (error) {
    console.error('获取浏览量统计数据失败:', error)
    toast.error('获取浏览量统计数据失败')
  } finally {
    viewStatsLoading.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    toast.success("已复制到剪贴板");
  }).catch(() => {
    toast.error("复制失败");
  });
}

// ==================== 数据指标详情弹窗 ====================
type MetricType = 'totalView' | 'todayView' | 'totalLike' | 'todayLike'

const metricConfig: Record<MetricType, {
  title: string
  eventType: string
  accent: string
  highlight: 'total' | 'today'
}> = {
  totalView: {title: '总浏览量详情', eventType: 'VIEW', accent: '#f97316', highlight: 'total'},
  todayView: {title: '今日浏览量详情', eventType: 'VIEW', accent: '#6366f1', highlight: 'today'},
  totalLike: {title: '总点赞量详情', eventType: 'LIKE', accent: '#f43f5e', highlight: 'total'},
  todayLike: {title: '今日点赞量详情', eventType: 'LIKE', accent: '#ec4899', highlight: 'today'},
}

const showDetailModal = ref(false)
const detailLoading = ref(false)
const detailMetricType = ref<MetricType>('totalView')
const detailData = ref<any>(null)
const todaySentenceDetails = ref<any[]>([])

const detailTitle = computed(() => metricConfig[detailMetricType.value].title)

const hasLikePrefix = computed(() => detailMetricType.value.includes('Like'))

// 概要卡片数据（前两个可点击切换视图）
interface SummaryCard {
  key: string
  label: string
  value: number
  accent: string
  active: boolean
  clickable: boolean
  metric: MetricType
}

const detailSummaryCards = computed<SummaryCard[]>(() => {
  const mt = detailMetricType.value
  const likePrefix = hasLikePrefix.value
  return [
    {
      key: 'total',
      label: likePrefix ? '总点赞量' : '总浏览量',
      value: detailData.value?.totalViewCount || 0,
      accent: metricConfig[likePrefix ? 'totalLike' : 'totalView'].accent,
      active: mt === 'totalView' || mt === 'totalLike',
      clickable: true,
      metric: likePrefix ? 'totalLike' : 'totalView',
    },
    {
      key: 'today',
      label: likePrefix ? '今日点赞量' : '今日浏览量',
      value: detailData.value?.todayViewCount || 0,
      accent: metricConfig[likePrefix ? 'todayLike' : 'todayView'].accent,
      active: mt === 'todayView' || mt === 'todayLike',
      clickable: true,
      metric: likePrefix ? 'todayLike' : 'todayView',
    },
    {
      key: '30d',
      label: '近30天累计',
      value: detailCategoryTotal.value,
      accent: '#6b7280',
      active: false,
      clickable: false,
      metric: mt,
    },
    {
      key: 'avg',
      label: '日均',
      value: detailDailyAverage.value,
      accent: '#6b7280',
      active: false,
      clickable: false,
      metric: mt,
    },
  ]
})

// 近30天分类聚合（按时间序列求和）
const detailCategoryBreakdown = computed(() => {
  if (!detailData.value?.timeSeries) return []
  const categoryMap = new Map<string, { categoryName: string; displayName: string; count: number }>()
  for (const point of detailData.value.timeSeries) {
    for (const detail of (point.details || [])) {
      const existing = categoryMap.get(detail.categoryName)
      if (existing) {
        existing.count += detail.count || 0
      } else {
        categoryMap.set(detail.categoryName, {
          categoryName: detail.categoryName,
          displayName: detail.displayName || detail.categoryName,
          count: detail.count || 0,
        })
      }
    }
  }
  return Array.from(categoryMap.values()).sort((a, b) => b.count - a.count)
})

// 近30天总量
const detailCategoryTotal = computed(() =>
  detailCategoryBreakdown.value.reduce((sum, item) => sum + item.count, 0),
)

// 日均
const detailDailyAverage = computed(() => {
  if (!detailData.value?.timeSeries || detailData.value.timeSeries.length === 0) return 0
  return Math.round(detailCategoryTotal.value / 30)
})

// 详情趋势图配置
const detailChartOption = computed(() => {
  const echartsData = detailData.value?.echartsData
  if (!echartsData || !echartsData.series || echartsData.series.length === 0) return null

  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16']
  return {
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
    legend: {
      data: echartsData.series.map((s: any) => s.displayName || s.name),
      top: 0,
      right: 0,
      type: 'scroll',
    },
    grid: {left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true},
    xAxis: {
      type: 'category',
      data: echartsData.xAxis,
      name: '日期',
      axisLabel: {rotate: echartsData.xAxis.length > 10 ? 30 : 0, interval: 0},
    },
    yAxis: {type: 'value', name: '数据量'},
    series: echartsData.series.map((s: any, index: number) => ({
      name: s.displayName || s.name,
      type: 'line',
      data: s.data,
      smooth: s.smooth,
      lineStyle: {width: 2, color: colors[index % colors.length]},
      symbol: 'circle',
      symbolSize: 6,
      areaStyle: {opacity: 0.1, color: colors[index % colors.length]},
    })),
  }
})

// 点击弹窗内概要卡片切换视图
const handleSummaryCardClick = (metricType: MetricType) => {
  if (metricType === detailMetricType.value) return
  loadDetailByMetric(metricType)
}

// 按指标类型加载弹窗数据
const loadDetailByMetric = async (metricType: MetricType) => {
  detailMetricType.value = metricType
  detailLoading.value = true
  detailData.value = null
  todaySentenceDetails.value = []
  try {
    const cfg = metricConfig[metricType]
    const response = await overviewV1alpha1ApiClient.overview.getViewStatistics({
      params: {days: 30, granularity: 'day', eventType: cfg.eventType},
    })
    detailData.value = response.data

    // 今日浏览/点赞 → 同时拉取句子维度详情
    if (metricType === 'todayLike' || metricType === 'todayView') {
      const sentenceRes = await axiosInstance.get(
        '/apis/console.api.hitokotohub.puresky.top/v1alpha1/overview/today-sentence-details',
        {params: {eventType: cfg.eventType}},
      )
      todaySentenceDetails.value = (sentenceRes.data as any).sentences || []
    }
  } catch (error) {
    console.error('获取详细统计数据失败:', error)
    toast.error('获取详细统计数据失败')
  } finally {
    detailLoading.value = false
  }
}

// 点击指标卡片，拉取详细数据并打开弹窗
const handleStatClick = async (metricType: MetricType) => {
  showDetailModal.value = true
  await loadDetailByMetric(metricType)
}
</script>

<style scoped lang="scss">
@use '../styles/variables.scss' as *;
@media (min-width: 1024px) {
  .charts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.custom-card {
  border-radius: 1rem;
  border: 1px solid #e5e7eb !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
  will-change: transform;
  margin-bottom: 0;
}

.custom-card:hover {
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.08);
  transform: translate3d(0, -2px, 0);
}

:deep(.el-card__body) {
  padding: 1.5rem;
}

:deep(.el-card__header) {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

:deep(.el-table) {
  border: none !important;
  border-radius: 1rem;
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

:deep(.el-progress-bar__outer) {
  background-color: #f1f5f9;
  border-radius: 9999px;
}

:deep(.el-progress-bar__inner) {
  border-radius: 9999px;
}

:deep(.el-tag) {
  border: none;
  font-weight: 500;
}

:deep(.echarts) {
  width: 100% !important;
  overflow: hidden;
}

.grid {
  gap: 1.5rem;
}

.space-y-6 > * + * {
  margin-top: 1.5rem;
}

/* ============================ 可点击指标卡片 ============================ */
.stat-clickable {
  cursor: pointer;
  user-select: none;
}

.stat-clickable:hover {
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
  transform: translate3d(0, -3px, 0);
}

.stat-clickable:active {
  transform: translate3d(0, -1px, 0);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

/* ============================ 详情弹窗 ============================ */
.detail-modal-body {
  min-height: 200px;
  padding: 4px 0;
}

.detail-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 概要指标卡片 */
.detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

@media (max-width: 640px) {
  .detail-summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.detail-summary-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f9fafb;
  border: 1px solid #f0f0f0;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.detail-summary-card.is-active {
  background: #fff;
  border-width: 1.5px;
}

.detail-summary-card.is-clickable {
  cursor: pointer;
  user-select: none;
}

.detail-summary-card.is-clickable:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.detail-summary-label {
  font-size: 12px;
  color: #9ca3af;
  letter-spacing: 0.02em;
}

.detail-summary-value {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

/* 区块标题 */
.detail-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

/* 趋势图 */
.detail-chart-section {
  margin-bottom: 20px;
}

.detail-empty-chart {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  margin-bottom: 20px;
}

/* 分类占比列表 */
.detail-breakdown-section {
  margin-top: 4px;
}

.detail-breakdown-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.detail-breakdown-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f9fafb;
  transition: background 0.15s ease;
}

.detail-breakdown-row:hover {
  background: #f3f4f6;
}

.detail-breakdown-rank {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 11px;
  font-weight: 600;
}

.detail-breakdown-row:first-child .detail-breakdown-rank {
  background: $rose-500;
  color: #fff;
}

.detail-breakdown-row:nth-child(2) .detail-breakdown-rank {
  background: #f97316;
  color: #fff;
}

.detail-breakdown-row:nth-child(3) .detail-breakdown-rank {
  background: #eab308;
  color: #fff;
}

.detail-breakdown-name {
  flex-shrink: 0;
  width: 120px;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-breakdown-bar-wrap {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #f3f4f6;
  overflow: hidden;
  min-width: 60px;
}

.detail-breakdown-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.detail-breakdown-count {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  min-width: 50px;
  text-align: right;
}

.detail-breakdown-pct {
  flex-shrink: 0;
  font-size: 12px;
  color: #9ca3af;
  min-width: 48px;
  text-align: right;
}

@media (max-width: 640px) {
  .detail-breakdown-name {
    width: 80px;
    font-size: 12px;
  }

  .detail-breakdown-count {
    min-width: 40px;
    font-size: 12px;
  }

  .detail-breakdown-pct {
    min-width: 40px;
    font-size: 11px;
  }
}

/* ============================ 句子详情列表 ============================ */
.detail-sentence-section {
  margin-top: 4px;
}

.detail-sentence-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

.detail-sentence-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f9fafb;
  border: 1px solid #f0f0f0;
  transition: background 0.15s ease, box-shadow 0.15s ease;
}

.detail-sentence-item:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.detail-sentence-body {
  flex: 1;
  min-width: 0;
}

.detail-sentence-content {
  font-size: 13px;
  color: #1f2937;
  line-height: 1.5;
  margin: 0 0 6px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.detail-sentence-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #9ca3af;
}

.detail-sentence-sep {
  color: #e5e7eb;
}

.detail-sentence-stats {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fff0f3;
  min-width: 52px;
}

.detail-sentence-event-count {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
}

.detail-sentence-stats.is-like .detail-sentence-event-count {
  color: #f43f5e;
}

.detail-sentence-stats.is-view .detail-sentence-event-count {
  color: #6366f1;
}

.detail-sentence-event-label {
  font-size: 10px;
  color: #9ca3af;
  margin-top: 2px;
}

.detail-sentence-stats.is-like {
  background: #fff0f3;
}

.detail-sentence-stats.is-view {
  background: #eef2ff;
}

/* 排名徽章额外样式 */
.detail-breakdown-rank.is-first {
  background: $rose-500;
  color: #fff;
}

.detail-breakdown-rank.is-top3 {
  color: #fff;
}

.detail-breakdown-row:first-child .detail-breakdown-rank,
.detail-breakdown-rank.is-first {
  background: $rose-500;
  color: #fff;
}

.detail-breakdown-row:nth-child(2) .detail-breakdown-rank {
  background: #f97316;
  color: #fff;
}

.detail-breakdown-row:nth-child(3) .detail-breakdown-rank,
.detail-breakdown-rank.is-top3 {
  background: #eab308;
  color: #fff;
}

@media (max-width: 640px) {
  .detail-sentence-stats {
    min-width: 42px;
    padding: 4px 8px;
  }

  .detail-sentence-event-count {
    font-size: 16px;
  }

  .detail-sentence-content {
    font-size: 12px;
  }
}
</style>
