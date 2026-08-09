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
          @load="handleCardLoaded"
          @error="handleCardError"
        />
        <div v-if="loading" class="share-preview-status">
          <VLoading size="sm" text="卡片生成中..." />
        </div>
        <div v-else-if="cardError" class="share-preview-status share-preview-status--error">
          卡片生成失败，请稍后重试
        </div>
      </div>

      <!-- 句子信息 -->
      <div class="share-sentence-info">
        <div class="share-sentence-content">
          {{ payload?.content || sentence?.spec.content }}
        </div>
        <div class="share-sentence-meta">
          <VTag>作者：{{ payload?.author || sentence?.spec.author || '匿名' }}</VTag>
          <VTag>来源：{{ payload?.source || sentence?.spec.source || '未知' }}</VTag>
          <VTag v-if="payload?.categoryDisplayName">
            分类：{{ payload.categoryDisplayName }}
          </VTag>
          <VTag>点赞：{{ payload?.likeCount ?? 0 }}</VTag>
          <VTag>浏览：{{ payload?.viewCount ?? 0 }}</VTag>
        </div>
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
import {VButton, VLoading, VModal, VTag} from '@halo-dev/components'
import {CopyDocument, Download, Link} from '@element-plus/icons-vue'
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
let loadToken = 0

const cardSrc = computed(() =>
  props.sentence?.metadata?.name
    ? sentenceShareConsoleApiClient.getShareCardUrl(props.sentence.metadata.name)
    : '',
)

const resetState = () => {
  payload.value = null
  loading.value = false
  cardError.value = false
  cardLoaded.value = false
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
    ctx.fillStyle = '#0a0606'
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
}

.share-preview-status {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  width: 100%;
  color: #6b7280;
  font-size: 13px;
}

.share-preview-status--error {
  color: #b91c1c;
}

.share-sentence-info {
  margin-bottom: 16px;
}

.share-sentence-content {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  line-height: 1.6;
  margin-bottom: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

.share-sentence-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
