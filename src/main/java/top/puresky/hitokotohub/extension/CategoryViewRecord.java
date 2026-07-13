package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = "hitokotohub.puresky.top",
    version = "v1alpha1",
    kind = "CategoryViewRecord",
    plural = "categoryviewrecords",
    singular = "categoryviewrecord"
)
public class CategoryViewRecord extends AbstractExtension {

    public enum EventType {
        VIEW,
        LIKE,
        /**
         * 已废弃：不再产生新的 UNLIKE 统计记录。取消点赞改为删除对应的 LIKE 记录。
         * 保留枚举值仅为兼容历史数据，避免反序列化失败。
         */
        @Deprecated
        UNLIKE
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    public static class Spec {

        @Schema(description = "分类名称（唯一标识）")
        private String categoryName;

        @Schema(description = "事件类型：VIEW / LIKE（UNLIKE 已废弃）")
        private EventType eventType;

        @Schema(description = "句子 metadata name，用于按句子维度统计浏览量/点赞量")
        private String sentenceName;

        @Schema(description = "客户端 IP，仅 LIKE 事件使用，用于定位对应的点赞记录")
        private String ip;
    }
}
