import {describe, expect, it, vi} from 'vitest';
import {usePagination} from '../usePagination';

describe('usePagination', () => {
  it('默认初始值：page=1, size=20, total=0', () => {
    const {page, size, total} = usePagination();
    expect(page.value).toBe(1);
    expect(size.value).toBe(20);
    expect(total.value).toBe(0);
  });

  it('自定义初始值：initialPage=2, initialSize=10', () => {
    const {page, size} = usePagination({initialPage: 2, initialSize: 10});
    expect(page.value).toBe(2);
    expect(size.value).toBe(10);
  });

  it('handlePageChange 更新 page 并触发 onChange 回调', async () => {
    const onChange = vi.fn();
    const {page, handlePageChange} = usePagination({onChange});
    await handlePageChange(3);
    expect(page.value).toBe(3);
    expect(onChange).toHaveBeenCalledWith(3, 20);
  });

  it('handleSizeChange 更新 size、重置 page 为 1 并触发 onChange', async () => {
    const onChange = vi.fn();
    const {page, size, handleSizeChange} = usePagination({
      initialPage: 5,
      onChange,
    });
    await handleSizeChange(50);
    expect(size.value).toBe(50);
    expect(page.value).toBe(1);
    expect(onChange).toHaveBeenCalledWith(1, 50);
  });

  it('resetPage 重置 page 为 1（不触发 onChange）', () => {
    const onChange = vi.fn();
    const {page, resetPage} = usePagination({initialPage: 5, onChange});
    resetPage();
    expect(page.value).toBe(1);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('setTotal 设置总条数', () => {
    const {total, setTotal} = usePagination();
    setTotal(100);
    expect(total.value).toBe(100);
  });
});
