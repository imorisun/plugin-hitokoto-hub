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
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;

@Component
@RequiredArgsConstructor
public class SentenceReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Sentence.class, request.name()).ifPresent(sentence -> {
            String categoryName = sentence.getSpec().getCategoryName();

            client.fetch(Category.class, categoryName).ifPresentOrElse(
                category -> {
                    ListOptions options = ListOptions.builder()
                        .fieldQuery(Queries.and(
                            Queries.equal("spec.categoryName", categoryName),
                            Queries.isNull("metadata.deletionTimestamp")
                        ))
                        .build();
                    long count = client.countBy(Sentence.class, options);

                    if (category.getStatus() == null) {
                        category.setStatus(new Category.Status());
                    }
                    category.getStatus().setSentenceCount(count);
                    client.update(category);
                },
                () -> client.delete(sentence)
            );

            if (ExtensionOperator.isDeleted(sentence)) {
                sentence.getMetadata().setFinalizers(Collections.emptySet());
                client.update(sentence);
            }
        });
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Sentence()).build();
    }
}