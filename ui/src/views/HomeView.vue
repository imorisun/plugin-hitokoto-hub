<script setup lang="ts">
import { defineAsyncComponent, shallowRef, watch } from 'vue'
import { useRouteQuery } from '@vueuse/router'
import { VPageHeader, VTabbar, VButton, IconExternalLinkLine, VLoading } from '@halo-dev/components'
import IconHitokotoLogo from '~icons/my-icons/hitokoto-logo'

const Overview = defineAsyncComponent({
  loader: () => import('@/components/Overview.vue'),
  loadingComponent: VLoading,
})

const SentenceList = defineAsyncComponent({
  loader: () => import('@/components/SentenceList.vue'),
  loadingComponent: VLoading,
})

const SubmissionList = defineAsyncComponent({
  loader: () => import('@/components/SubmissionList.vue'),
  loadingComponent: VLoading,
})

const AiGenerateLogList = defineAsyncComponent({
  loader: () => import('@/components/AiGenerateLogList.vue'),
  loadingComponent: VLoading,
})

const SimilarityCheck = defineAsyncComponent({
  loader: () => import('@/components/SimilarityCheck.vue'),
  loadingComponent: VLoading,
})

const tabs = shallowRef([
  {
    id: 'Overview',
    label: '概览',
  },
  {
    id: 'SentenceList',
    label: '数据列表',
  },
  {
    id: 'SubmissionList',
    label: '访客提交',
  },
  {
    id: 'AiGenerateLog',
    label: 'AI 日志',
  },
  {
    id: 'SimilarityCheck',
    label: '相似度检查',
  },
])

const activeIndex = useRouteQuery<string>('tab', tabs.value[0].id)

watch(
  activeIndex,
  (value) => {
    if (!tabs.value.some((tab) => tab.id === value)) {
      activeIndex.value = 'SentenceList'
    }
  },
  { immediate: true },
)

const handleRouteToFront = () => {
  window.open('/hitokoto', '_blank')
}
</script>

<template>
  <VPageHeader title="轻言数据管理">
    <template #icon>
      <IconHitokotoLogo></IconHitokotoLogo>
    </template>
    <template #actions>
      <VButton @click="handleRouteToFront" size="sm" ghost>
        <template #icon>
          <IconExternalLinkLine></IconExternalLinkLine>
        </template>
        跳转到前台
      </VButton>
    </template>
  </VPageHeader>

  <div class="m-0 space-y-4 md:m-4">
    <div class="border-b border-gray-100 bg-white">
      <VTabbar
        v-model:active-id="activeIndex"
        :items="tabs.map((item) => ({ id: item.id, label: item.label }))"
        class="w-full rounded-none!"
        type="outline"
      ></VTabbar>
    </div>
    <Overview v-if="activeIndex == 'Overview'" />
    <SentenceList v-if="activeIndex == 'SentenceList'" />
    <SubmissionList v-if="activeIndex == 'SubmissionList'" />
    <AiGenerateLogList v-if="activeIndex == 'AiGenerateLog'" />
    <SimilarityCheck v-if="activeIndex == 'SimilarityCheck'" />
  </div>
</template>
