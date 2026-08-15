<script setup lang="ts">
import {markRaw, shallowRef, watch} from 'vue'
import SentenceList from '@/components/SentenceList.vue'
import Overview from '@/components/Overview.vue'
import AiGenerateLogList from '@/components/AiGenerateLogList.vue'
import SubmissionList from '@/components/SubmissionList.vue'
import SimilarityCheck from '@/components/SimilarityCheck.vue'
import {useRouteQuery} from '@vueuse/router'
import {VPageHeader, VTabbar} from '@halo-dev/components'
import IconHitokotoLogo from '~icons/my-icons/hitokoto-logo';

const tabs = shallowRef([
  {
    id: 'Overview',
    label: '概览',
    component: markRaw(Overview),
  },
  {
    id: 'SentenceList',
    label: '数据列表',
    component: markRaw(SentenceList),
  },
  {
    id: 'SubmissionList',
    label: '访客提交',
    component: markRaw(SubmissionList),
  },
  {
    id: 'AiGenerateLog',
    label: 'AI 日志',
    component: markRaw(AiGenerateLogList),
  },
  {
    id: 'SimilarityCheck',
    label: '相似度检查',
    component: markRaw(SimilarityCheck),
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
        {immediate: true},
)
</script>

<template>
  <VPageHeader title="轻言数据管理">
    <template #icon>
      <IconHitokotoLogo></IconHitokotoLogo>
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
    <Overview v-if="activeIndex == 'Overview'"/>
    <SentenceList v-if="activeIndex == 'SentenceList'"/>
    <SubmissionList v-if="activeIndex == 'SubmissionList'"/>
    <AiGenerateLogList v-if="activeIndex == 'AiGenerateLog'"/>
    <SimilarityCheck v-if="activeIndex == 'SimilarityCheck'"/>
  </div>
</template>
