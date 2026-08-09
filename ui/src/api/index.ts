import {axiosInstance} from '@halo-dev/api-client'
import type {Category, SentenceList} from './generated'
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

/**
 * 带实时句子数量的分类视图。
 *
 * <p>与后端 {@code CategoryConsoleEndpoint.CategoryWithCount} 对应，
 * 替代已移除的 {@code Category.Status.sentenceCount} 缓存字段。
 */
export interface CategoryWithCount extends Category {
  sentenceCount: number
}

/** 分页结果容器，与后端 ListResult 对应。 */
export interface ListResultCategoryWithCount {
  page: number
  size: number
  total: number
  items: CategoryWithCount[]
}

/**
 * 句子分享数据载荷，与后端 {@code SharePayload} 对应。
 * sharePath 为相对路径（如 /hitokoto?sentence=xxx），
 * 完整链接由前端基于 location.origin 拼装，后端不感知域名。
 */
export interface SharePayload {
  name: string
  content: string
  author: string
  source: string
  categoryName: string
  categoryDisplayName: string
  likeCount: number
  viewCount: number
  sharePath: string
  siteName: string
  createdAt: number
}

const categoryCoreApiClient = {
  category: new CategoryV1alpha1Api(undefined, '', axiosInstance),
  // 新增：带实时句子数量的分类列表查询（替代 listCategory + status.sentenceCount）
  listCategoriesWithCounts: (params: { page?: number; size?: number }) =>
    axiosInstance.get<ListResultCategoryWithCount>(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/categories',
      {params}
    ),
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

/** 控制台分享接口：管理员可分享任意句子（含未发布）。 */
const sentenceShareConsoleApiClient = {
  getShare: (name: string) =>
    axiosInstance.get<SharePayload>(
      `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence/${encodeURIComponent(name)}/share`,
    ),
  getShareCardUrl: (name: string) =>
    `/apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence/${encodeURIComponent(name)}/share/card`,
}

export {
  categoryCoreApiClient,
  sentenceCoreApiClient,
  overviewV1alpha1ApiClient,
  sentencePublicV1alpha1ApiClient,
  categoryViewRecordV1alpha1ApiClient,
  sentenceShareConsoleApiClient,
}
