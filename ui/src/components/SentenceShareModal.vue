<template>
  <VModal
    v-model:visible="modalVisible"
    title="分享句子"
    :width="480"
  >
    <div class="share-modal-body">
      <!-- 分享卡片预览 -->
      <div class="share-preview-wrap">
        <img
          v-if="cardSrc && !cardError"
          :src="cardSrc"
          class="share-card-preview"
          alt="分享卡片预览"
          :class="{ 'share-card-preview--fading': loading }"
          @load="handleCardLoaded"
          @error="handleCardError"
        />
        <div v-if="loading" class="share-preview-status">
          <VLoading size="sm" text="卡片生成中..." />
        </div>
        <div v-else-if="cardError" class="share-preview-status share-preview-status--error">
          卡片生成失败，请稍后重试
        </div>
        <button
          class="share-theme-toggle"
          type="button"
          :aria-label="cardTheme === 'dark' ? '切换到日间卡片' : '切换到夜间卡片'"
          :title="cardTheme === 'dark' ? '切换到日间卡片' : '切换到夜间卡片'"
          @click="toggleCardTheme"
        >
          <el-icon v-if="cardTheme === 'dark'"><Sunny /></el-icon>
          <el-icon v-else><Moon /></el-icon>
        </button>
      </div>

      <!-- 分享操作 -->
      <div class="share-actions">
        <VButton :loading="copying" @click="handleCopyLink">
          <template #icon>
            <el-icon :size="15"><Link /></el-icon>
          </template>
          复制链接
        </VButton>
        <VButton @click="handleDownloadCard">
          <template #icon>
            <el-icon :size="15"><Download /></el-icon>
          </template>
          保存图片
        </VButton>
        <VButton :loading="copying" @click="handleCopyText">
          <template #icon>
            <el-icon :size="15"><CopyDocument /></el-icon>
          </template>
          复制内容
        </VButton>
      </div>
    </div>
    <template #footer>
      <div class="modal-footer">
        <VButton @click="modalVisible = false">关闭</VButton>
      </div>
    </template>
  </VModal>
</template>

<script setup lang="ts">
import {VButton, VLoading, VModal} from '@halo-dev/components'
import {CopyDocument, Download, Link, Moon, Sunny} from '@element-plus/icons-vue'
import {computed, ref, watch} from 'vue'
import {sentenceShareConsoleApiClient} from '@/api'
import type {SharePayload} from '@/api'
import type {Sentence} from '@/api/generated'
import {useToast} from '@/composables/useToast'

const props = defineProps<{
  visible: boolean
  sentence: Sentence | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const toast = useToast()

const modalVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const payload = ref<SharePayload | null>(null)
const loading = ref(false)
const cardError = ref(false)
const cardLoaded = ref(false)
const copying = ref(false)
/* 卡片主题：dark（夜间）/ light（日间），默认夜间与品牌调性一致 */
const cardTheme = ref<'dark' | 'light'>('dark')
let loadToken = 0

const cardSrc = computed(() =>
  props.sentence?.metadata?.name
    ? sentenceShareConsoleApiClient.getShareCardUrl(
        props.sentence.metadata.name,
        cardTheme.value,
      )
    : '',
)

/** 切换卡片日间/夜间主题并重新加载预览 */
const toggleCardTheme = () => {
  cardTheme.value = cardTheme.value === 'dark' ? 'light' : 'dark'
  cardLoaded.value = false
  loading.value = true
  cardError.value = false
}

const resetState = () => {
  payload.value = null
  loading.value = false
  cardError.value = false
  cardLoaded.value = false
  cardTheme.value = 'dark'
}

const loadShareData = async () => {
  const name = props.sentence?.metadata?.name
  if (!name) return
  const token = ++loadToken
  resetState()
  loading.value = true
  try {
    const {data} = await sentenceShareConsoleApiClient.getShare(name)
    if (token !== loadToken) return
    payload.value = data
  } catch (e) {
    console.error('获取分享数据失败', e)
    if (token === loadToken) {
      toast.error('获取分享数据失败')
      cardError.value = true
    }
  } finally {
    if (token === loadToken) {
      loading.value = false
    }
  }
}

const handleCardLoaded = () => {
  cardLoaded.value = true
  loading.value = false
}

const handleCardError = () => {
  cardError.value = true
  loading.value = false
}

const copyToClipboard = async (text: string): Promise<boolean> => {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
    throw new Error('clipboard api unavailable')
  } catch (e) {
    try {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.focus()
      ta.select()
      const ok = document.execCommand('copy')
      ta.remove()
      if (ok) return true
    } catch (e2) {
      /* 忽略降级失败 */
    }
    return false
  }
}

const handleCopyLink = async () => {
  if (!payload.value?.sharePath) {
    toast.warning('分享链接暂不可用')
    return
  }
  copying.value = true
  try {
    const link = window.location.origin + payload.value.sharePath
    const ok = await copyToClipboard(link)
    ok ? toast.success('分享链接已复制') : toast.error('复制失败，请手动复制')
  } finally {
    copying.value = false
  }
}

const handleCopyText = async () => {
  if (!payload.value) {
    toast.warning('分享内容暂不可用')
    return
  }
  copying.value = true
  try {
    let text = payload.value.content || ''
    if (payload.value.author) {
      text += ` —— ${payload.value.author}`
    }
    if (payload.value.source) {
      text += `《${payload.value.source}》`
    }
    const ok = await copyToClipboard(text)
    ok ? toast.success('句子内容已复制') : toast.error('复制失败，请手动复制')
  } finally {
    copying.value = false
  }
}

const handleDownloadCard = () => {
  if (!cardLoaded.value) {
    toast.warning('卡片尚未生成完成')
    return
  }
  try {
    /* SVG 绘制到 canvas 再导出 PNG，保证跨浏览器下载效果一致 */
    const img = document.querySelector('.share-card-preview') as HTMLImageElement | null
    if (!img) return
    const canvas = document.createElement('canvas')
    canvas.width = img.naturalWidth || 600
    canvas.height = img.naturalHeight || 800
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    /* 背景色与卡片主题一致，避免导出 PNG 出现黑色底板 */
    ctx.fillStyle = cardTheme.value === 'dark' ? '#0a0606' : '#fdf8f7'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    const a = document.createElement('a')
    a.download = `hitokoto-card-${props.sentence?.metadata?.name || 'share'}.png`
    a.href = canvas.toDataURL('image/png')
    a.click()
    toast.success('卡片已保存')
  } catch (e) {
    console.error('保存卡片失败', e)
    toast.error('图片生成失败，请稍后再试')
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      loadShareData()
    } else {
      loadToken++
      resetState()
    }
  },
)
</script>

<style scoped>
.share-modal-body {
  padding: 4px 0;
}

.share-preview-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  max-height: 46vh;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
  margin-bottom: 16px;
  /* 内边距让卡片四周留白，上下边距均匀 */
  padding: 8px;
}

.share-card-preview {
  display: block;
  max-width: 100%;
  max-height: calc(46vh - 16px);
  width: 100%;
  object-fit: contain;
  /* 主题切换过渡：旧卡片淡出微缩，新卡片加载完成后淡入 */
  transition: opacity 0.4s ease, transform 0.4s ease;
}

.share-card-preview--fading {
  opacity: 0;
  transform: scale(0.985);
}

/* 卡片日间/夜间主题切换按钮：悬浮于预览右上角 */
.share-theme-toggle {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.85);
  color: #6b7280;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.share-theme-toggle:hover {
  color: #e11d48;
  border-color: #e11d48;
}

.share-theme-toggle:active {
  transform: scale(0.92);
}

.share-preview-status {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  width: 100%;
  color: #6b7280;
  font-size: 13px;
  /* 与卡片淡出/淡入节奏一致的柔和过渡 */
  animation: statusFadeIn 0.4s ease both;
}

@keyframes statusFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.share-preview-status--error {
  color: #b91c1c;
}

.share-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.share-actions .el-button {
  width: 100%;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
