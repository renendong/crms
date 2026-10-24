/** Excel 批量导入统一回执。 */
export interface ImportResultVO {
  total: number
  success: number
  failed: number
  errors: { row: number; message: string }[]
}
