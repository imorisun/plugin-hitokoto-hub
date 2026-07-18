package top.puresky.hitokotohub.service.similarity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 泛型并查集（Union-Find）数据结构，支持路径压缩。
 *
 * <p>用于将传递相似的元素归为同一组：若 A~B 且 B~C，则 A、B、C 归为一组。
 *
 * <p>时间复杂度：find/union 均为 O(α(n))，α 为反阿克曼函数（近似常数）。
 *
 * <p>提取自 {@code SimilarityCheckServiceImpl} 的嵌套类并泛化，
 * 原实现仅支持 String 节点，现支持任意类型 T。
 *
 * @param <T> 节点类型
 */
public final class UnionFind<T> {

    private final Map<T, T> parent = new HashMap<>();

    /** 添加节点（若已存在则忽略）。 */
    public void add(T x) {
        parent.putIfAbsent(x, x);
    }

    /**
     * 查找根节点（带路径压缩）。
     *
     * @param x 起始节点
     * @return 根节点，若 x 不存在则返回 x 本身
     */
    public T find(T x) {
        T p = parent.get(x);
        if (p == null) {
            return x;
        }
        if (!p.equals(x)) {
            p = find(p);
            parent.put(x, p);
        }
        return p;
    }

    /**
     * 合并两个节点所在的集合。
     *
     * @param a 节点1
     * @param b 节点2
     */
    public void union(T a, T b) {
        T ra = find(a);
        T rb = find(b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }

    /**
     * 按根节点分组。
     *
     * @return 根节点 → 成员集合
     */
    public Map<T, Set<T>> groupByRoot() {
        Map<T, Set<T>> groups = new HashMap<>();
        for (T name : parent.keySet()) {
            groups.computeIfAbsent(find(name), k -> new HashSet<>()).add(name);
        }
        return groups;
    }

    /**
     * 获取所有已注册的节点。
     *
     * @return 节点集合
     */
    public Set<T> allNames() {
        return parent.keySet();
    }
}
