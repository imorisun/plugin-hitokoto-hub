import {describe, expect, it} from 'vitest';
import {nextTick, ref, type Ref} from 'vue';
import {
  runWithConcurrency,
  summarizeBatchResult,
  useBatchSelection,
} from '../useBatchSelection';

interface Item {
  id: string;
  label: string;
}

const makeItems = (ids: string[]): Item[] => ids.map((id) => ({id, label: `项 ${id}`}));

const setup = (
  initialIds: string[] = [],
  resetWatch: ReadonlyArray<Ref<unknown>> = [],
) => {
  const items = ref<Item[]>(makeItems(initialIds));
  const selection = useBatchSelection<Item>({
    getId: (item) => item.id,
    items,
    resetWatch,
  });
  return {items, selection};
};

describe('useBatchSelection - 基本选择', () => {
  it('初始状态：无选中，selectedCount=0，hasSelection=false', () => {
    const {selection} = setup(['a', 'b', 'c']);
    expect(selection.selectedCount.value).toBe(0);
    expect(selection.hasSelection.value).toBe(false);
    expect(selection.selectedIdList.value).toEqual([]);
    expect(selection.isSelected('a')).toBe(false);
  });

  it('select 选中单个 ID', () => {
    const {selection} = setup(['a', 'b']);
    selection.select('a');
    expect(selection.isSelected('a')).toBe(true);
    expect(selection.selectedCount.value).toBe(1);
    expect(selection.hasSelection.value).toBe(true);
    expect(selection.selectedIdList.value).toEqual(['a']);
  });

  it('select 重复 ID 无副作用', () => {
    const {selection} = setup(['a']);
    selection.select('a');
    selection.select('a');
    expect(selection.selectedCount.value).toBe(1);
  });

  it('deselect 取消选中', () => {
    const {selection} = setup(['a', 'b']);
    selection.select('a');
    selection.select('b');
    selection.deselect('a');
    expect(selection.isSelected('a')).toBe(false);
    expect(selection.isSelected('b')).toBe(true);
    expect(selection.selectedCount.value).toBe(1);
  });

  it('deselect 未选中 ID 无副作用', () => {
    const {selection} = setup(['a']);
    selection.select('a');
    selection.deselect('not-exist');
    expect(selection.selectedCount.value).toBe(1);
  });

  it('toggle 切换选中状态', () => {
    const {selection} = setup(['a']);
    selection.toggle('a');
    expect(selection.isSelected('a')).toBe(true);
    selection.toggle('a');
    expect(selection.isSelected('a')).toBe(false);
  });
});

describe('useBatchSelection - 当前页全选', () => {
  it('selectAllOnPage 选中当前页所有项', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.selectAllOnPage();
    expect(selection.selectedCount.value).toBe(3);
    expect(selection.isAllSelectedOnPage.value).toBe(true);
    expect(selection.isIndeterminateOnPage.value).toBe(false);
  });

  it('clearAllOnPage 取消当前页所有项', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.selectAllOnPage();
    selection.clearAllOnPage();
    expect(selection.selectedCount.value).toBe(0);
    expect(selection.isAllSelectedOnPage.value).toBe(false);
  });

  it('isAllSelectedOnPage：空列表返回 false', () => {
    const {selection} = setup([]);
    expect(selection.isAllSelectedOnPage.value).toBe(false);
    expect(selection.isIndeterminateOnPage.value).toBe(false);
  });

  it('isIndeterminateOnPage：部分选中返回 true', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.select('a');
    expect(selection.isAllSelectedOnPage.value).toBe(false);
    expect(selection.isIndeterminateOnPage.value).toBe(true);
  });

  it('isIndeterminateOnPage：全选时返回 false', () => {
    const {selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    expect(selection.isIndeterminateOnPage.value).toBe(false);
  });

  it('toggleAllOnPage：从无选中切换到全选', () => {
    const {selection} = setup(['a', 'b']);
    selection.toggleAllOnPage();
    expect(selection.isAllSelectedOnPage.value).toBe(true);
  });

  it('toggleAllOnPage：从全选切换到无选中', () => {
    const {selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    selection.toggleAllOnPage();
    expect(selection.isAllSelectedOnPage.value).toBe(false);
    expect(selection.selectedCount.value).toBe(0);
  });

  it('toggleAllOnPage：从半选切换到全选', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.select('a');
    selection.toggleAllOnPage();
    expect(selection.isAllSelectedOnPage.value).toBe(true);
    expect(selection.selectedCount.value).toBe(3);
  });
});

describe('useBatchSelection - 跨页选择', () => {
  it('selectAllOnPage 仅选中当前页项，保留之前选中', () => {
    const {items, selection} = setup(['a']);
    selection.select('old-id');
    items.value = makeItems(['a', 'b']);
    selection.selectAllOnPage();
    expect(selection.selectedIdList.value).toEqual(expect.arrayContaining(['old-id', 'a', 'b']));
    expect(selection.selectedCount.value).toBe(3);
  });

  it('clearAllOnPage 仅取消当前页项，保留其他页选中', () => {
    const {items, selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    items.value = makeItems(['c', 'd']);
    selection.selectAllOnPage();
    // 当前页切换为 [c,d]，原 [a,b] 仍在选择集中
    expect(selection.selectedCount.value).toBe(4);
    items.value = makeItems(['c', 'd']);
    selection.clearAllOnPage();
    expect(selection.selectedIdList.value).toEqual(expect.arrayContaining(['a', 'b']));
    expect(selection.selectedCount.value).toBe(2);
  });

  it('isAllSelectedOnPage 仅反映当前页状态，不影响其他页', () => {
    const {items, selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    items.value = makeItems(['c', 'd']);
    expect(selection.isAllSelectedOnPage.value).toBe(false);
    expect(selection.isIndeterminateOnPage.value).toBe(false);
  });
});

describe('useBatchSelection - 清空与清理', () => {
  it('clear 清空所有选择', () => {
    const {selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    selection.clear();
    expect(selection.selectedCount.value).toBe(0);
    expect(selection.hasSelection.value).toBe(false);
  });

  it('clear 空选择集无副作用', () => {
    const {selection} = setup(['a']);
    selection.clear();
    expect(selection.selectedCount.value).toBe(0);
  });

  it('prune 移除已不存在的项（数组入参）', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.selectAllOnPage();
    selection.prune(['a', 'b']);
    expect(selection.selectedIdList.value).toEqual(['a', 'b']);
  });

  it('prune 移除已不存在的项（Set 入参）', () => {
    const {selection} = setup(['a', 'b', 'c']);
    selection.selectAllOnPage();
    selection.prune(new Set(['a', 'b']));
    expect(selection.selectedIdList.value).toEqual(['a', 'b']);
  });

  it('prune 无需移除时不触发响应式更新', () => {
    const {selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    const before = selection.selectedIds.value;
    selection.prune(['a', 'b']);
    expect(selection.selectedIds.value).toBe(before);
  });
});

describe('useBatchSelection - resetWatch 自动重置', () => {
  it('resetWatch 依赖变化时自动清空选择', async () => {
    const page = ref(1);
    const {items, selection} = setup(['a', 'b'], [page]);
    selection.selectAllOnPage();
    expect(selection.selectedCount.value).toBe(2);
    page.value = 2;
    await nextTick();
    expect(selection.selectedCount.value).toBe(0);
  });

  it('resetWatch 为空时不自动重置', async () => {
    const {items, selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    items.value = makeItems(['c', 'd']);
    await nextTick();
    // items 变化不会触发 reset（resetWatch 为空）
    expect(selection.selectedCount.value).toBe(2);
  });
});

describe('useBatchSelection - 数据变化时的表现', () => {
  it('当前页数据变化但 resetWatch 未触发时，选择保留', async () => {
    const {items, selection} = setup(['a', 'b']);
    selection.selectAllOnPage();
    // 模拟删除一项后刷新当前页
    items.value = makeItems(['a']);
    await nextTick();
    expect(selection.isSelected('a')).toBe(true);
    expect(selection.isSelected('b')).toBe(true); // 仍在选择集中
  });

  it('删除后通过 prune 清理脏数据', () => {
    const {items, selection} = setup(['a', 'b', 'c']);
    selection.selectAllOnPage();
    items.value = makeItems(['a', 'b']); // c 被删除
    selection.prune(items.value.map((i) => i.id));
    expect(selection.selectedIdList.value).toEqual(['a', 'b']);
  });
});

describe('runWithConcurrency - 并发执行', () => {
  it('空任务数组立即返回空结果', async () => {
    const result = await runWithConcurrency([]);
    expect(result).toEqual([]);
  });

  it('所有任务成功时全部 fulfilled', async () => {
    const tasks = [1, 2, 3].map((n) => () => Promise.resolve(n * 2));
    const result = await runWithConcurrency(tasks);
    expect(result).toHaveLength(3);
    expect(result.every((r) => r.status === 'fulfilled')).toBe(true);
    expect((result[0] as PromiseFulfilledResult<number>).value).toBe(2);
    expect((result[1] as PromiseFulfilledResult<number>).value).toBe(4);
    expect((result[2] as PromiseFulfilledResult<number>).value).toBe(6);
  });

  it('部分任务失败时保留 rejection', async () => {
    const tasks = [
      () => Promise.resolve('ok'),
      () => Promise.reject(new Error('fail')),
      () => Promise.resolve('ok2'),
    ];
    const result = await runWithConcurrency(tasks);
    expect(result).toHaveLength(3);
    expect(result[0].status).toBe('fulfilled');
    expect(result[1].status).toBe('rejected');
    expect(result[2].status).toBe('fulfilled');
  });

  it('结果顺序与任务顺序一致', async () => {
    const tasks = [10, 20, 30, 40, 50].map((n) => () =>
      new Promise<number>((resolve) => setTimeout(() => resolve(n), Math.random() * 50)),
    );
    const result = await runWithConcurrency(tasks, 2);
    const values = result.map((r) => (r as PromiseFulfilledResult<number>).value);
    expect(values).toEqual([10, 20, 30, 40, 50]);
  });

  it('并发数不超过指定值', async () => {
    let running = 0;
    let maxRunning = 0;
    const tasks = Array.from({length: 10}, () => async () => {
      running++;
      maxRunning = Math.max(maxRunning, running);
      await new Promise((r) => setTimeout(r, 10));
      running--;
    });
    await runWithConcurrency(tasks, 3);
    expect(maxRunning).toBeLessThanOrEqual(3);
  });

  it('并发数超过任务数时自动收敛', async () => {
    const tasks = [() => Promise.resolve(1), () => Promise.resolve(2)];
    const result = await runWithConcurrency(tasks, 100);
    expect(result).toHaveLength(2);
  });
});

describe('summarizeBatchResult - 结果汇总', () => {
  it('正确统计成功与失败数量', () => {
    const results: PromiseSettledResult<string>[] = [
      {status: 'fulfilled', value: 'a'},
      {status: 'rejected', reason: new Error('x')},
      {status: 'fulfilled', value: 'b'},
      {status: 'rejected', reason: 'err'},
    ];
    const summary = summarizeBatchResult(results);
    expect(summary.success).toBe(2);
    expect(summary.failed).toBe(2);
    expect(summary.errors).toHaveLength(2);
  });

  it('空结果数组返回 0/0', () => {
    const summary = summarizeBatchResult([]);
    expect(summary.success).toBe(0);
    expect(summary.failed).toBe(0);
    expect(summary.errors).toEqual([]);
  });

  it('全部成功时 failed=0', () => {
    const results: PromiseSettledResult<number>[] = [
      {status: 'fulfilled', value: 1},
      {status: 'fulfilled', value: 2},
    ];
    const summary = summarizeBatchResult(results);
    expect(summary.success).toBe(2);
    expect(summary.failed).toBe(0);
    expect(summary.errors).toEqual([]);
  });
});
