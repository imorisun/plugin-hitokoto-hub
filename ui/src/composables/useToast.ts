import {Toast} from '@halo-dev/components';

/**
 * Toast 通知 composable。
 *
 * <p>薄包装 @halo-dev/components 的 Toast，提供 success/error/warning/info 方法。
 * 确保项目中 Toast 调用方式统一，便于后续扩展（如埋点、国际化）。
 *
 * <p>项目硬约束：Toast 的 z-index 必须为 500，确保在 modal(300) 和 dropdown(400) 之上可见。
 * 该 z-index 由 variables.scss 的 --z-toast 变量定义，Toast 组件内部已遵循。
 *
 * @returns success/error/warning/info 方法
 */
export function useToast() {
  return {
    /** 显示成功通知。 */
    success: (message: string) => Toast.success(message),
    /** 显示错误通知。 */
    error: (message: string) => Toast.error(message),
    /** 显示警告通知。 */
    warning: (message: string) => Toast.warning(message),
    /** 显示信息通知。 */
    info: (message: string) => Toast.info(message),
  };
}
