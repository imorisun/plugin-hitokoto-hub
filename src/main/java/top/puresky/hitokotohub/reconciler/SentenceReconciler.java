package top.puresky.hitokotohub.reconciler;

import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionOperator;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;

@Component
@RequiredArgsConstructor
public class SentenceReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Sentence.class, request.name()).ifPresent(sentence -> {
            // 处理句子删除
            if (ExtensionOperator.isDeleted(sentence)) {
                String categoryName = sentence.getSpec().getCategoryName();
                updateCategoryCount(categoryName);
                sentence.getMetadata().setFinalizers(Collections.emptySet());
                client.update(sentence);
                return;
            }

            String categoryName = sentence.getSpec().getCategoryName();

            // 如果分类名为空或分类不存在，归入"未分类"
            if (categoryName == null || categoryName.isBlank()
                || client.fetch(Category.class, categoryName).isEmpty()) {

                if (!UncategorizedConstants.METADATA_NAME.equals(categoryName)) {
                    // 仅当 categoryName 不是 uncategorized 时才更新，避免无限循环
                    sentence.getSpec().setCategoryName(UncategorizedConstants.METADATA_NAME);
                    client.update(sentence);
                    return; // 更新后由下一次 reconcile 处理计数
                }
            }

            updateCategoryCount(categoryName);
        });
        return Result.doNotRetry();
    }

    private void updateCategoryCount(String categoryName) {
        String effectiveName = (categoryName == null || categoryName.isBlank())
            ? UncategorizedConstants.METADATA_NAME : categoryName;

        client.fetch(Category.class, effectiveName).ifPresent(category -> {
            ListOptions options = ListOptions.builder()
                .fieldQuery(Queries.and(
                    Queries.equal("spec.categoryName", effectiveName),
                    Queries.isNull("metadata.deletionTimestamp")
                ))
                .build();
            long count = client.countBy(Sentence.class, options);

            if (category.getStatus() == null) {
                category.setStatus(new Category.Status());
            }
            category.getStatus().setSentenceCount(count);
            client.update(category);
        });
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Sentence()).build();
    }
}
