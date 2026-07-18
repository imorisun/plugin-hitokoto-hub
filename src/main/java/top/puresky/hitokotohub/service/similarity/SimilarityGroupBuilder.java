package top.puresky.hitokotohub.service.similarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import top.puresky.hitokotohub.extension.SimilarityGroup;
import top.puresky.hitokotohub.extension.SimilarityGroup.SentenceInfo;

/**
 * 相似度分组构建器（边界层，可依赖 Extension DTO）。
 *
 * <p>职责：
 * <ul>
 *   <li>从相似对列表 + 句子数据构建 {@link SimilarityGroup} 分组列表</li>
 *   <li>收集非最优句子名称（用于批量删除）</li>
 *   <li>分页与空结果</li>
 * </ul>
 *
 * <p>提取自 {@code SimilarityCheckServiceImpl} 的 buildGroupsResult/buildGroup/
 * buildSimilarityMap/collectNonOptimalNames/paginateGroups/emptyGroupsResult 方法。
 * 数据访问（fetch）仍留在 Service 中，本类仅处理纯逻辑。
 */
@Component
public class SimilarityGroupBuilder {

    /**
     * 从相似对和句子数据构建分组列表。
     *
     * <p>流程：并查集分组 → 预构建相似度查找表 → 过滤已删除句子 → 选最优 → 构建分组信息
     *
     * @param pairs        相似对列表（已解析）
     * @param profileMap   句子名称 → 纯数据投影（已过滤已删除句子，缺失的句子不会出现）
     * @return 分组列表（按相似句子数量降序），无相似对时返回空列表
     */
    public List<SimilarityGroup> buildGroups(List<SentencePair> pairs,
                                              Map<String, SentenceProfile> profileMap) {
        if (pairs.isEmpty()) {
            return Collections.emptyList();
        }

        // 并查集分组
        UnionFind<String> uf = new UnionFind<>();
        for (SentencePair pair : pairs) {
            uf.add(pair.sentence1Name());
            uf.add(pair.sentence2Name());
            uf.union(pair.sentence1Name(), pair.sentence2Name());
        }
        Map<String, Set<String>> groupMembers = uf.groupByRoot();

        // 预构建相似度查找表：key = "name1|name2"（按字典序），O(1) 查找
        Map<String, Double> similarityMap = buildSimilarityMap(pairs);

        // 构建每个分组
        List<SimilarityGroup> groups = new ArrayList<>();
        for (Set<String> memberNames : groupMembers.values()) {
            if (memberNames.size() < 2) continue;

            SimilarityGroup group = buildGroup(memberNames, profileMap, similarityMap);
            if (group != null) {
                groups.add(group);
            }
        }

        // 按相似句子数量降序
        groups.sort(Comparator.comparingInt(SimilarityGroup::getSimilarCount).reversed());
        return groups;
    }

    /**
     * 收集所有非最优句子名称（每组保留评分最高的，其余标记删除）。
     *
     * @param pairs      相似对列表（重新计算的完整列表）
     * @param profileMap 句子名称 → 纯数据投影
     * @return 待删除句子名称集合
     */
    public Set<String> collectNonOptimalNames(List<SentencePair> pairs,
                                               Map<String, SentenceProfile> profileMap) {
        if (pairs.isEmpty()) {
            return Collections.emptySet();
        }

        UnionFind<String> uf = new UnionFind<>();
        for (SentencePair pair : pairs) {
            uf.add(pair.sentence1Name());
            uf.add(pair.sentence2Name());
            uf.union(pair.sentence1Name(), pair.sentence2Name());
        }
        Map<String, Set<String>> groupMembers = uf.groupByRoot();

        Set<String> toDelete = new HashSet<>();
        for (Set<String> memberNames : groupMembers.values()) {
            if (memberNames.size() < 2) continue;

            // 找出评分最高的句子作为最优
            SentenceProfile best = null;
            double bestScore = -1;
            for (String name : memberNames) {
                SentenceProfile p = profileMap.get(name);
                if (p == null) continue;
                double score = SentenceScorer.score(p);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }

            // 其余全部标记删除
            String bestName = best != null ? best.name() : null;
            for (String name : memberNames) {
                if (!name.equals(bestName)) {
                    toDelete.add(name);
                }
            }
        }
        return toDelete;
    }

    /**
     * 对分组列表进行分页。
     *
     * @param groups 全量分组列表
     * @param page   页码（从 1 开始）
     * @param size   每页数量
     * @return 包含 page/size/total/groups 的 Map
     */
    public Map<String, Object> paginate(List<SimilarityGroup> groups, int page, int size) {
        int total = groups.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<SimilarityGroup> pageGroups = fromIndex < total
            ? groups.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> result = new HashMap<>(4);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("groups", pageGroups);
        return result;
    }

    /**
     * 构建空分组结果。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 空结果的 Map
     */
    public Map<String, Object> emptyResult(int page, int size) {
        Map<String, Object> result = new HashMap<>(4);
        result.put("page", page);
        result.put("size", size);
        result.put("total", 0);
        result.put("groups", Collections.emptyList());
        return result;
    }

    // ===================== 内部方法 =====================

    /**
     * 构建相似度查找表。
     *
     * <p>Key 格式：{@code min(name1, name2) + "|" + max(name1, name2)}
     */
    private Map<String, Double> buildSimilarityMap(List<SentencePair> pairs) {
        Map<String, Double> map = new HashMap<>(pairs.size());
        for (SentencePair pair : pairs) {
            String key = similarityKey(pair.sentence1Name(), pair.sentence2Name());
            map.put(key, pair.similarity());
        }
        return map;
    }

    /** 生成相似度查找的 key（字典序小的在前）。 */
    private String similarityKey(String name1, String name2) {
        return name1.compareTo(name2) <= 0
            ? name1 + "|" + name2
            : name2 + "|" + name1;
    }

    /** 查找两个句子之间的相似度，未找到返回 0。 */
    private double getSimilarity(Map<String, Double> similarityMap, String name1, String name2) {
        return similarityMap.getOrDefault(similarityKey(name1, name2), 0.0);
    }

    /**
     * 构建单个相似分组。
     *
     * @param memberNames  组内句子名称集合
     * @param profileMap   句子名称 → 纯数据投影
     * @param similarityMap 相似度查找表
     * @return 分组对象，有效句子不足 2 个返回 null
     */
    private SimilarityGroup buildGroup(Set<String> memberNames,
                                        Map<String, SentenceProfile> profileMap,
                                        Map<String, Double> similarityMap) {
        // 过滤已删除句子
        List<SentenceProfile> groupProfiles = memberNames.stream()
            .map(profileMap::get)
            .filter(p -> p != null)
            .collect(Collectors.toList());

        if (groupProfiles.size() < 2) {
            return null;
        }

        // 按评分降序，第一个为最优
        groupProfiles.sort(Comparator.comparingDouble(p -> -SentenceScorer.score(p)));

        SentenceProfile best = groupProfiles.get(0);
        double bestScore = SentenceScorer.score(best);

        // 构建相似句子列表
        List<SentenceInfo> similarInfos = new ArrayList<>(groupProfiles.size() - 1);
        double maxSim = 0;
        double sumSim = 0;

        for (int i = 1; i < groupProfiles.size(); i++) {
            SentenceProfile other = groupProfiles.get(i);
            double sim = getSimilarity(similarityMap, best.name(), other.name());
            maxSim = Math.max(maxSim, sim);
            sumSim += sim;
            similarInfos.add(buildSentenceInfo(other, SentenceScorer.score(other), sim));
        }

        // 按相似度降序
        similarInfos.sort(Comparator.comparingDouble(SentenceInfo::getSimilarity).reversed());

        int similarCount = similarInfos.size();
        double avgSim = similarCount > 0 ? sumSim / similarCount : 0;

        return SimilarityGroup.builder()
            .groupId(best.name())
            .bestSentence(buildSentenceInfo(best, bestScore, 0))
            .bestSentenceScore(bestScore)
            .similarSentences(similarInfos)
            .similarCount(similarCount)
            .maxSimilarity(Math.round(maxSim * 10000.0) / 10000.0)
            .avgSimilarity(Math.round(avgSim * 10000.0) / 10000.0)
            .build();
    }

    /** 从 SentenceProfile 构建 SentenceInfo。 */
    private SentenceInfo buildSentenceInfo(SentenceProfile p, double score, double similarity) {
        return SentenceInfo.builder()
            .name(p.name())
            .content(p.content())
            .category(p.categoryName())
            .author(p.author())
            .source(p.source())
            .published(p.published())
            .likeCount(p.likeCount())
            .viewCount(p.viewCount())
            .score(score)
            .similarity(similarity)
            .build();
    }
}
