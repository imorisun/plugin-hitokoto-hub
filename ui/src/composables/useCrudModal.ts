import {ref, type Ref} from 'vue';

/**
 * 表单弹窗 CRUD 状态管理 composable。
 *
 * <p>统一管理 showFormModal/isEditing/saving/formData 状态，
 * 提供 handleCreate/handleEdit/handleSave/closeForm 方法。
 *
 * <p>saveFn 由调用方提供，包含具体的 API 调用与业务校验逻辑。
 *
 * @param options.createForm 创建新表单时的初始值工厂函数
 * @param options.saveFn 保存函数，接收 (formData, isEditing)，抛出异常表示保存失败
 * @param options.onSuccess 保存成功后的回调（通常 Toast.success + refresh）
 * @param options.onError 保存失败后的回调（通常 Toast.error）
 */
export function useCrudModal<T>(options: {
  createForm: () => T;
  saveFn: (data: T, isEditing: boolean) => Promise<void>;
  onSuccess?: () => void | Promise<void>;
  onError?: (e: unknown) => void;
}) {
  const showFormModal = ref(false);
  const isEditing = ref(false);
  const saving = ref(false);
  const formData = ref(options.createForm()) as Ref<T>;

  /** 打开创建弹窗：重置表单为初始值。 */
  const handleCreate = () => {
    isEditing.value = false;
    formData.value = options.createForm();
    showFormModal.value = true;
  };

  /**
   * 打开编辑弹窗：填入已有数据。
   *
   * @param prefilledForm 从领域对象映射出的表单数据
   */
  const handleEdit = (prefilledForm: T) => {
    isEditing.value = true;
    formData.value = prefilledForm;
    showFormModal.value = true;
  };

  /** 保存：调用 saveFn，成功后关闭弹窗，失败时回调 onError。 */
  const handleSave = async () => {
    saving.value = true;
    try {
      await options.saveFn(formData.value, isEditing.value);
      showFormModal.value = false;
      await options.onSuccess?.();
    } catch (e) {
      options.onError?.(e);
    } finally {
      saving.value = false;
    }
  };

  /** 关闭弹窗。 */
  const closeForm = () => {
    showFormModal.value = false;
  };

  /** 重置表单为初始值。 */
  const resetForm = () => {
    formData.value = options.createForm();
  };

  return {
    showFormModal,
    isEditing,
    saving,
    formData,
    handleCreate,
    handleEdit,
    handleSave,
    closeForm,
    resetForm,
  };
}
