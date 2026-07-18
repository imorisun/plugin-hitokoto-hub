import {ref, type Ref} from 'vue';

/**
 * 异步列表加载 composable。
 *
 * <p>统一管理 loading/list/total 状态，封装 try-catch-finally 模式。
 * 调用方提供 fetchFn（读取组件局部状态如 page/size/keyword/filters，返回 items+total）
 * 与可选的 onError 回调。
 *
 * @param options.fetchFn 数据获取函数，返回 { items, total }
 * @param options.onError 异常回调（通常 Toast.error）
 * @returns loading/list/total 响应式状态 + refresh 方法
 */
export function useAsyncTable<T>(options: {
  fetchFn: () => Promise<{ items: T[]; total: number }>;
  onError?: (e: unknown) => void;
}) {
  const loading = ref(false);
  const list = ref<T[]>([]) as Ref<T[]>;
  const total = ref(0);

  /**
   * 刷新列表：设置 loading，调用 fetchFn，更新 list/total。
   * 异常时调用 onError，finally 恢复 loading。
   */
  const refresh = async () => {
    loading.value = true;
    try {
      const result = await options.fetchFn();
      list.value = result.items || [];
      total.value = result.total || 0;
    } catch (e) {
      options.onError?.(e);
    } finally {
      loading.value = false;
    }
  };

  return {
    loading,
    list,
    total,
    refresh,
  };
}
