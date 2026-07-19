package top.puresky.hitokotohub.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * ReactiveExtensionClient 的 Mockito mock 工厂，内置内存存储。
 *
 * <p>覆盖项目中实际使用的 8 个方法：listAll / listBy / fetch / get / create / update / delete /
 * countBy。所有返回的 Mono/Flux 都是同步完成的（{@code Mono.just}/{@code Flux.just}），
 * 因此被测代码中的 {@code .block()} 调用是安全的，不会死锁。
 *
 * <p>说明：ListOptions 的字段过滤未实现，listAll/countBy 返回该类型全部记录。
 * 若测试需要精确过滤行为，可在返回的 mock 上追加 {@code when(...).thenReturn(...)} 覆盖。
 *
 * <p>使用示例：
 * <pre>{@code
 * ReactiveExtensionClient client = MockExtensionClient.builder()
 *     .with(s1).with(s2).with(category)
 *     .build();
 * }</pre>
 */
public final class MockExtensionClient {

    private final Map<Class<?>, Map<String, Extension>> store = new ConcurrentHashMap<>();

    private MockExtensionClient() {}

    /** 创建一个新的 builder。 */
    public static MockExtensionClient builder() {
        return new MockExtensionClient();
    }

    /** 注册一个 extension 到内存存储（按其 metadata.name 索引）。 */
    public MockExtensionClient with(Extension ext) {
        if (ext == null || ext.getMetadata() == null || ext.getMetadata().getName() == null) {
            throw new IllegalArgumentException("Extension 必须含 metadata.name");
        }
        store.computeIfAbsent(ext.getClass(), k -> new ConcurrentHashMap<>())
            .put(ext.getMetadata().getName(), ext);
        return this;
    }

    /** 批量注册。 */
    public MockExtensionClient withAll(Iterable<? extends Extension> exts) {
        if (exts != null) {
            exts.forEach(this::with);
        }
        return this;
    }

    /** 构造 mock 实例。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ReactiveExtensionClient build() {
        ReactiveExtensionClient mock = Mockito.mock(ReactiveExtensionClient.class);

        // listAll(Class, ListOptions, Sort) -> 返回该类型全部记录
        Mockito.when(mock.listAll(Mockito.<Class<Extension>>any(), Mockito.any(), Mockito.any()))
            .thenAnswer(inv -> {
                Class<Extension> cls = inv.getArgument(0);
                return Flux.fromIterable(allOf(cls));
            });

        // listBy(Class, ListOptions, PageRequest) -> 分页返回
        Mockito.when(mock.listBy(Mockito.<Class<Extension>>any(), Mockito.any(), Mockito.any()))
            .thenAnswer(inv -> {
                Class<Extension> cls = inv.getArgument(0);
                List<Extension> all = allOf(cls);
                // PageRequest 实现了 SortableRequest 的相关接口；这里简单返回全部（足够多数测试用）
                int page = 1;
                int size = all.size();
                Object pageRequest = inv.getArgument(2);
                try {
                    // PageRequestImpl 有 getPage/getSize
                    page = (int) pageRequest.getClass().getMethod("getPage").invoke(pageRequest);
                    size = (int) pageRequest.getClass().getMethod("getSize").invoke(pageRequest);
                } catch (Exception ignored) {
                    // 保留默认值
                }
                int from = Math.min((page - 1) * size, all.size());
                int to = Math.min(from + size, all.size());
                List<Extension> pageItems = new ArrayList<>(all.subList(from, to));
                return Mono.just(new ListResult<>(page, size, (long) all.size(), pageItems));
            });

        // fetch(Class, String name)
        Mockito.when(mock.fetch(Mockito.<Class<Extension>>any(), Mockito.anyString()))
            .thenAnswer(inv -> {
                Class<Extension> cls = inv.getArgument(0);
                String name = inv.getArgument(1);
                Extension e = bucket(cls).get(name);
                return e != null ? Mono.just(e) : Mono.empty();
            });

        // get(Class, String name) —— fetch 的别名（SentencePublicEndpoint 使用）
        Mockito.when(mock.get(Mockito.<Class<Extension>>any(), Mockito.anyString()))
            .thenAnswer(inv -> {
                Class<Extension> cls = inv.getArgument(0);
                String name = inv.getArgument(1);
                Extension e = bucket(cls).get(name);
                return e != null ? Mono.just(e) : Mono.error(new RuntimeException("Not found: " + name));
            });

        // create(Extension) -> 存入并返回
        Mockito.when(mock.create(Mockito.any(Extension.class)))
            .thenAnswer(inv -> {
                Extension ext = inv.getArgument(0);
                if (ext.getMetadata() != null && ext.getMetadata().getName() != null) {
                    store.computeIfAbsent(ext.getClass(), k -> new ConcurrentHashMap<>())
                        .put(ext.getMetadata().getName(), ext);
                }
                return Mono.just(ext);
            });

        // update(Extension) -> 替换并返回
        Mockito.when(mock.update(Mockito.any(Extension.class)))
            .thenAnswer(inv -> {
                Extension ext = inv.getArgument(0);
                if (ext.getMetadata() != null && ext.getMetadata().getName() != null) {
                    store.computeIfAbsent(ext.getClass(), k -> new ConcurrentHashMap<>())
                        .put(ext.getMetadata().getName(), ext);
                }
                return Mono.just(ext);
            });

        // delete(Extension) -> 移除
        Mockito.when(mock.delete(Mockito.any(Extension.class)))
            .thenAnswer(inv -> {
                Extension ext = inv.getArgument(0);
                if (ext.getMetadata() != null && ext.getMetadata().getName() != null) {
                    bucket(ext.getClass()).remove(ext.getMetadata().getName());
                }
                return Mono.just(ext);
            });

        // countBy(Class, ListOptions) -> 该类型记录数
        Mockito.when(mock.countBy(Mockito.<Class<Extension>>any(), Mockito.any()))
            .thenAnswer(inv -> {
                Class<Extension> cls = inv.getArgument(0);
                return Mono.just((long) bucket(cls).size());
            });

        return mock;
    }

    @SuppressWarnings("unchecked")
    private <E extends Extension> Map<String, E> bucket(Class<E> cls) {
        return (Map<String, E>) store.computeIfAbsent(cls, k -> new ConcurrentHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private <E extends Extension> List<E> allOf(Class<E> cls) {
        return new ArrayList<>(bucket(cls).values());
    }
}
