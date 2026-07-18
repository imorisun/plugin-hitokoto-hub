package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link UnionFind} 单元测试。
 *
 * <p>覆盖传递性合并、路径压缩、分组、未注册节点查找等。
 */
@DisplayName("UnionFind 并查集")
class UnionFindTest {

    @Test
    @DisplayName("传递合并：A~B、B~C 后三者归入同一组")
    void shouldMergeTransitively() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("A");
        uf.add("B");
        uf.add("C");
        uf.union("A", "B");
        uf.union("B", "C");

        Map<String, Set<String>> groups = uf.groupByRoot();

        assertThat(groups).hasSize(1);
        Set<String> members = groups.values().iterator().next();
        assertThat(members).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    @DisplayName("未合并的节点各自成组")
    void shouldKeepUnmergedNodesSeparate() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("A");
        uf.add("B");
        uf.add("C");
        uf.union("A", "B");

        Map<String, Set<String>> groups = uf.groupByRoot();

        assertThat(groups).hasSize(2);
        assertThat(groups.values()).anySatisfy(set ->
            assertThat(set).containsExactlyInAnyOrder("A", "B"));
        assertThat(groups.values()).anySatisfy(set ->
            assertThat(set).containsExactly("C"));
    }

    @Test
    @DisplayName("find：未注册节点返回自身")
    void shouldReturnSelfForUnregisteredNode() {
        UnionFind<String> uf = new UnionFind<>();
        assertThat(uf.find("ghost")).isEqualTo("ghost");
    }

    @Test
    @DisplayName("find：路径压缩后所有节点指向同一根")
    void shouldCompressPathOnFind() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("A");
        uf.add("B");
        uf.add("C");
        uf.add("D");
        uf.union("A", "B");
        uf.union("B", "C");
        uf.union("C", "D");

        String root = uf.find("A");

        assertThat(uf.find("A")).isEqualTo(root);
        assertThat(uf.find("B")).isEqualTo(root);
        assertThat(uf.find("C")).isEqualTo(root);
        assertThat(uf.find("D")).isEqualTo(root);
        assertThat(uf.groupByRoot()).hasSize(1);
    }

    @Test
    @DisplayName("add：重复添加同一节点不产生副作用")
    void shouldIgnoreDuplicateAdd() {
        UnionFind<Integer> uf = new UnionFind<>();
        uf.add(1);
        uf.add(1);
        uf.add(1);

        assertThat(uf.allNames()).hasSize(1);
        assertThat(uf.allNames()).contains(1);
    }

    @Test
    @DisplayName("union：相同根的两节点 union 后分组不变")
    void shouldNotChangeGroupWhenUnionSameRoot() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("A");
        uf.add("B");
        uf.union("A", "B");
        uf.union("A", "B");

        assertThat(uf.groupByRoot()).hasSize(1);
    }

    @Test
    @DisplayName("groupByRoot：空并查集返回空 Map")
    void shouldReturnEmptyMapForEmptyUnionFind() {
        UnionFind<String> uf = new UnionFind<>();
        assertThat(uf.groupByRoot()).isEmpty();
        assertThat(uf.allNames()).isEmpty();
    }

    @Test
    @DisplayName("泛型支持：Integer 节点正常工作")
    void shouldSupportGenericNodeType() {
        UnionFind<Integer> uf = new UnionFind<>();
        uf.add(1);
        uf.add(2);
        uf.add(3);
        uf.union(1, 2);
        uf.union(2, 3);

        Map<Integer, Set<Integer>> groups = uf.groupByRoot();
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(1, 2, 3);
    }
}