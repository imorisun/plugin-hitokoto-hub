import {axiosInstance} from '@halo-dev/api-client'
import type {SentenceList} from './generated'
import {
  CategoryV1alpha1Api,
  CategoryViewRecordV1alpha1Api,
  OverviewV1alpha1Api,
  SentencePublicV1alpha1Api,
  SentenceV1alpha1Api
} from './generated'

interface QuerySentencesParams {
  page?: number
  size?: number
  keyword?: string
  categoryName?: string
  sort?: string
}

const categoryCoreApiClient = {
  category: new CategoryV1alpha1Api(undefined, '', axiosInstance),
}
const sentenceCoreApiClient = {
  sentence: new SentenceV1alpha1Api(undefined, '', axiosInstance),
  querySentences: (params: QuerySentencesParams) =>
    axiosInstance.get<SentenceList>('/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence', {
      params,
    }),
  clearUncategorizedSentences: () =>
    axiosInstance.delete<number>('/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence/-/clear-uncategorized'),
}
const overviewV1alpha1ApiClient = {
  overview: new OverviewV1alpha1Api(undefined, '', axiosInstance),
}

const sentencePublicV1alpha1ApiClient = {
  sentence: new SentencePublicV1alpha1Api(undefined, '', axiosInstance),
}
const categoryViewRecordV1alpha1ApiClient = {
  categoryViewRecord: new CategoryViewRecordV1alpha1Api(undefined, '', axiosInstance),
}

export {
  categoryCoreApiClient,
  sentenceCoreApiClient,
  overviewV1alpha1ApiClient,
  sentencePublicV1alpha1ApiClient,
  categoryViewRecordV1alpha1ApiClient
}
