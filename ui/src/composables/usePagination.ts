import {ref} from 'vue';

/**
 * 分页状态管理 composable。
 *
 * <p>提供 page/size/total 响应式状态与翻页回调，兼容两种分页组件：
 * <ul>
 *   <li>VPagination（@halo-dev/components）：使用 v-model:page/v-model:size 双向绑定</li>
 *   <li>el-pagination（Element Plus）：使用 @current-change/@size-change 事件</li>
 * </ul>
 *
 * @param options.initialPage 初始页码，默认 1
 * @param options.initialSize 初始每页数量，默认 20
 * @param options.onChange 翻页/改页大小时的回调（可选）
 * @returns page/size/total 响应式状态 + handlePageChange/handleSizeChange/resetPage/setTotal
 */
export function usePagination(options?: {
  initialPage?: number;
  initialSize?: number;
  onChange?: (page: number, size: number) => void | Promise<void>;
}) {
  const page = ref(options?.initialPage ?? 1);
  const size = ref(options?.initialSize ?? 20);
  const total = ref(0);

  /**
   * 页码变更处理器（用于 el-pagination @current-change）。
   * 更新 page 并触发 onChange 回调。
   */
  const handlePageChange = async (newPage: number) => {
    page.value = newPage;
    await options?.onChange?.(page.value, size.value);
  };

  /**
   * 每页数量变更处理器（用于 el-pagination @size-change）。
   * 更新 size，重置 page 为 1，并触发 onChange 回调。
   */
  const handleSizeChange = async (newSize: number) => {
    size.value = newSize;
    page.value = 1;
    await options?.onChange?.(page.value, size.value);
  };

  /** 重置页码为 1（不触发 onChange）。 */
  const resetPage = () => {
    page.value = 1;
  };

  /** 设置总条数。 */
  const setTotal = (t: number) => {
    total.value = t;
  };

  return {
    page,
    size,
    total,
    setTotal,
    handlePageChange,
    handleSizeChange,
    resetPage,
  };
}
