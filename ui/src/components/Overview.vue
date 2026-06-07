<template>
  <VCard>
    <div class="space-y-6">
      <!-- 概览卡片 -->
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3">
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
        <el-card class="custom-card">
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

        <el-card class="custom-card">
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
                <el-option label="取消点赞" value="UNLIKE" />
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
  </VCard>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {CircleCheck, CircleClose, Clock, Document, PriceTag, View} from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import {Toast, VCard} from "@halo-dev/components"
import {overviewV1alpha1ApiClient} from "@/api"

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
})

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
    Toast.error('获取浏览量统计数据失败')
  } finally {
    viewStatsLoading.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    Toast.success("已复制到剪贴板");
  }).catch(() => {
    Toast.error("复制失败");
  });
}
</script>

<style scoped>
@media (min-width: 1024px) {
  .charts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.custom-card {
  border-radius: 1rem;
  border: 1px solid #e5e7eb !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.3s, transform 0.3s;
  margin-bottom: 0;
}

.custom-card:hover {
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.07);
  transform: translateY(-2px);
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
</style>
