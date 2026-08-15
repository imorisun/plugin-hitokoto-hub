package top.puresky.hitokotohub.service.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * {@link UnionFind} 单元测试。
 */
class UnionFindTest {

    @Test
    void findReturnsSelfForUnknownNode() {
        UnionFind<String> uf = new UnionFind<>();
        assertEquals("x", uf.find("x"));
    }

    @Test
    void unionAndFind() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("a");
        uf.add("b");
        uf.union("a", "b");
        assertEquals(uf.find("a"), uf.find("b"));
    }

    @Test
    void transitivityGroupsThreeNodes() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("a");
        uf.add("b");
        uf.add("c");
        uf.union("a", "b");
        uf.union("b", "c");
        assertEquals(uf.find("a"), uf.find("c"));
    }

    @Test
    void groupByRootMergesTransitiveMembers() {
        UnionFind<Integer> uf = new UnionFind<>();
        for (int i = 1; i <= 4; i++) {
            uf.add(i);
        }
        uf.union(1, 2);
        uf.union(2, 3);
        var groups = uf.groupByRoot();
        assertEquals(2, groups.size(), "1-2-3 与 4 应各自成组");
        assertEquals(Set.of(1, 2, 3), groups.get(uf.find(1)));
        assertEquals(Set.of(4), groups.get(uf.find(4)));
    }

    @Test
    void allNamesReturnsRegisteredNodes() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("a");
        uf.add("b");
        assertEquals(Set.of("a", "b"), uf.allNames());
    }

    @Test
    void addIsIdempotent() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("a");
        uf.add("a");
        assertEquals(1, uf.allNames().size());
    }

    @Test
    void pathCompressionKeepsRootStable() {
        UnionFind<String> uf = new UnionFind<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String name = "n" + i;
            names.add(name);
            uf.add(name);
        }
        for (int i = 1; i < names.size(); i++) {
            uf.union(names.get(i - 1), names.get(i));
        }
        String root = uf.find(names.get(0));
        for (String name : names) {
            assertEquals(root, uf.find(name));
        }
        assertEquals(1, uf.groupByRoot().size());
    }

    @Test
    void groupByRootKeysAreCanonicalRoots() {
        UnionFind<String> uf = new UnionFind<>();
        uf.add("a");
        uf.add("b");
        uf.union("a", "b");
        var groups = uf.groupByRoot();
        Set<String> keys = groups.keySet();
        assertEquals(1, keys.size());
        assertTrue(keys.contains(uf.find("a")), "分组 key 应为规范化根节点");
        assertEquals(Set.of("a", "b"),
            groups.values().stream().flatMap(Set::stream).collect(Collectors.toSet()));
    }
}
