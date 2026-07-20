import {computed, ref, watch, type Ref} from 'vue';

/**
 * 批量选择状态管理 composable。
 *
 * <p>使用 {@link Set} 存储已选项的唯一标识，提供 O(1) 的查找/增删性能。
 * 适用于数据列表的全选/单选/取消选择等批量操作场景。
 *
 * <p>选择策略说明：
 * <ul>
 *   <li>跨页选择：默认不保留。当 {@link resetWatch} 中的依赖变化（如分页、筛选、排序）
 *       时自动清空选择，避免"看不到却已选"的混淆。</li>
 *   <li>数据刷新：当 {@link items} 因删除/刷新等操作变化但分页/筛选条件不变时，
 *       选择状态保留；已不存在的项可通过 {@link prune} 主动清理。</li>
 * </ul>
 *
 * <p>响应式说明：内部使用 {@link ref} 包装 {@link Set}，并通过创建新 Set 实例
 * 触发响应式更新（Vue 3 对 Set 的原生响应式支持有限，此方式更可靠）。
 *
 * @param options.getId 从数据项中提取唯一标识的函数
 * @param options.items 当前页面数据项列表的 ref（用于计算全选/半选状态）
 * @param options.resetWatch 监听这些 ref 变化时自动清空选择（通常为 page/size/filter/sort）
 * @returns 选择状态与操作方法
 */
export function useBatchSelection<T>(options: {
  getId: (item: T) => string;
  items: Ref<T[]>;
  resetWatch?: ReadonlyArray<Ref<unknown>>;
}) {
  const {getId, items, resetWatch = []} = options;

  /** 已选项 ID 集合。每次变更创建新 Set 实例以触发响应式。 */
  const selectedIds = ref<Set<string>>(new Set());

  /** 已选项数量。 */
  const selectedCount = computed(() => selectedIds.value.size);

  /** 是否有选中项。 */
  const hasSelection = computed(() => selectedIds.value.size > 0);

  /** 已选项 ID 数组（便于遍历与请求）。 */
  const selectedIdList = computed(() => Array.from(selectedIds.value));

  /** 判断指定 ID 是否已选中。 */
  const isSelected = (id: string): boolean => selectedIds.value.has(id);

  /** 当前页是否全选（当前页所有项均已选中）。空列表返回 false。 */
  const isAllSelectedOnPage = computed(() => {
    const list = items.value;
    if (list.length === 0) return false;
    return list.every((item) => selectedIds.value.has(getId(item)));
  });

  /** 当前页是否半选（部分选中）。空列表返回 false。 */
  const isIndeterminateOnPage = computed(() => {
    const list = items.value;
    if (list.length === 0) return false;
    let selectedOnPage = 0;
    for (const item of list) {
      if (selectedIds.value.has(getId(item))) {
        selectedOnPage++;
        if (selectedOnPage >= list.length) return false;
      }
    }
    return selectedOnPage > 0;
  });

  /** 选中指定 ID（若已存在则无操作）。 */
  const select = (id: string) => {
    if (selectedIds.value.has(id)) return;
    const next = new Set(selectedIds.value);
    next.add(id);
    selectedIds.value = next;
  };

  /** 取消选中指定 ID（若不存在则无操作）。 */
  const deselect = (id: string) => {
    if (!selectedIds.value.has(id)) return;
    const next = new Set(selectedIds.value);
    next.delete(id);
    selectedIds.value = next;
  };

  /** 切换指定 ID 的选中状态。 */
  const toggle = (id: string) => {
    if (selectedIds.value.has(id)) {
      deselect(id);
    } else {
      select(id);
    }
  };

  /** 选中当前页所有项。 */
  const selectAllOnPage = () => {
    if (items.value.length === 0) return;
    const next = new Set(selectedIds.value);
    for (const item of items.value) {
      next.add(getId(item));
    }
    selectedIds.value = next;
  };

  /** 取消选中当前页所有项。 */
  const clearAllOnPage = () => {
    if (items.value.length === 0) return;
    const next = new Set(selectedIds.value);
    for (const item of items.value) {
      next.delete(getId(item));
    }
    selectedIds.value = next;
  };

  /**
   * 切换当前页全选状态：
   * 若当前页已全选则全部取消，否则全选当前页。
   */
  const toggleAllOnPage = () => {
    if (isAllSelectedOnPage.value) {
      clearAllOnPage();
    } else {
      selectAllOnPage();
    }
  };

  /** 清空所有已选项（跨页）。 */
  const clear = () => {
    if (selectedIds.value.size === 0) return;
    selectedIds.value = new Set();
  };

  /**
   * 从选择集中移除已不存在的项。
   *
   * <p>用于删除操作后清理脏数据：被删除的项 ID 仍留在选择集中会导致
   * 后续操作失败，需主动清理。
   *
   * @param validIds 当前仍存在的 ID 集合
   */
  const prune = (validIds: string[] | Set<string>) => {
    const validSet = validIds instanceof Set ? validIds : new Set(validIds);
    let changed = false;
    const next = new Set<string>();
    for (const id of selectedIds.value) {
      if (validSet.has(id)) {
        next.add(id);
      } else {
        changed = true;
      }
    }
    if (changed) {
      selectedIds.value = next;
    }
  };

  // 监听指定依赖变化时自动重置选择（默认行为：分页/筛选/排序变化时清空选择）
  if (resetWatch.length > 0) {
    watch([...resetWatch], () => {
      clear();
    });
  }

  return {
    selectedIds,
    selectedCount,
    selectedIdList,
    hasSelection,
    isSelected,
    isAllSelectedOnPage,
    isIndeterminateOnPage,
    select,
    deselect,
    toggle,
    selectAllOnPage,
    clearAllOnPage,
    toggleAllOnPage,
    clear,
    prune,
  };
}

/**
 * 并发执行多个异步任务，限制最大并发数。
 *
 * <p>项目约定：批量删除/更新等 I/O 操作使用并发 16（参见 project_memory）。
 * 此工具函数避免引入额外依赖，提供一致的并发控制。
 *
 * @param tasks 任务工厂函数数组（每个返回一个 Promise）
 * @param concurrency 最大并发数，默认 16
 * @returns 所有任务的结算结果（与 Promise.allSettled 一致）
 */
export async function runWithConcurrency<T>(
  tasks: Array<() => Promise<T>>,
  concurrency = 16,
): Promise<PromiseSettledResult<T>[]> {
  if (tasks.length === 0) return [];
  const results: PromiseSettledResult<T>[] = new Array(tasks.length);
  let cursor = 0;

  const worker = async () => {
    while (true) {
      const index = cursor++;
      if (index >= tasks.length) return;
      try {
        const value = await tasks[index]();
        results[index] = {status: 'fulfilled', value};
      } catch (reason) {
        results[index] = {status: 'rejected', reason};
      }
    }
  };

  const workers: Promise<void>[] = [];
  const workerCount = Math.min(concurrency, tasks.length);
  for (let i = 0; i < workerCount; i++) {
    workers.push(worker());
  }
  await Promise.all(workers);
  return results;
}

/**
 * 汇总批量任务的执行结果。
 *
 * @param results {@link runWithConcurrency} 返回的结算结果数组
 * @returns `{success, failed, errors}` 统计信息
 */
export function summarizeBatchResult<T>(
  results: PromiseSettledResult<T>[],
): {
  success: number;
  failed: number;
  errors: unknown[];
} {
  let success = 0;
  let failed = 0;
  const errors: unknown[] = [];
  for (const r of results) {
    if (r.status === 'fulfilled') {
      success++;
    } else {
      failed++;
      errors.push(r.reason);
    }
  }
  return {success, failed, errors};
}
