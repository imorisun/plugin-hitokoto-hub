package top.puresky.hitokotohub.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.extension.Category;

/**
 * 启动时确保 {@link UncategorizedConstants#METADATA_NAME}（"未分类"）Category 实体存在。
 *
 * <p>"未分类" 是系统内置分类，用于：
 * <ul>
 *   <li>接收 categoryName 为空或不存在的 sentence（由 SentenceReconciler 归一化）</li>
 *   <li>接收所属分类被删除的 sentence（由 CategoryReconciler 迁移）</li>
 * </ul>
 *
 * <p>若该实体缺失，SentenceReconciler 的归一化逻辑虽然仍会指派 sentence 的 categoryName 为
 * "uncategorized"，但 CategoryCountService 在 listAll(Category) 时不会包含该分类，
 * 前端无法展示该分类的句子计数。本初始化器在启动时主动创建该实体，作为兜底保障。
 *
 * <p>容错策略：失败仅 log.error，不阻止插件启动。下次启动时会重试创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UncategorizedCategoryInitializer implements ApplicationRunner {

    private final ReactiveExtensionClient client;

    @Override
    public void run(ApplicationArguments args) {
        client.fetch(Category.class, UncategorizedConstants.METADATA_NAME)
            .switchIfEmpty(createUncategorized())
            .doOnError(e -> log.error("初始化「未分类」Category 失败", e))
            .onErrorResume(e -> reactor.core.publisher.Mono.empty())
            .subscribe(
                existing -> { /* 已存在或创建成功 */ },
                e -> log.error("初始化「未分类」Category 失败", e));
    }

    private reactor.core.publisher.Mono<Category> createUncategorized() {
        Category category = new Category();
        category.setMetadata(new Metadata());
        category.getMetadata().setName(UncategorizedConstants.METADATA_NAME);
        Category.Spec spec = new Category.Spec();
        spec.setName(UncategorizedConstants.DISPLAY_NAME);
        spec.setDescription(UncategorizedConstants.DESCRIPTION);
        category.setSpec(spec);

        return client.create(category)
            .doOnSuccess(c -> log.info("已创建「未分类」Category（metadata.name={}）",
                UncategorizedConstants.METADATA_NAME));
    }
}
