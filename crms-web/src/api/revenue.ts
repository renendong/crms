import http, { get, post, put, del } from './http'
import type { PageResult } from './customer'
import type { BatchPlanItem, ImportResultVO } from './payment'

export type { ImportResultVO, BatchPlanItem } from './payment'

export type IncomeType = 'GOODS' | 'SERVICE' | 'OTHER'

export interface RevenueRecordVO {
  id: string
  contractId: string
  contractCode?: string
  contractName?: string
  customerName?: string
  /** 收入归属期间 YYYY-MM */
  periodMonth: string
  recognizeDate: string
  incomeType: IncomeType
  /** 含税金额 */
  amount: number
  taxRate: number
  taxAmount: number
  netAmount: number
  costAmount: number
  grossProfit: number
  voucherNo?: string
  remark?: string
  version: number
}

export interface RevenueQuery {
  contractId?: string
  periodMonth?: string
  incomeType?: string
  voucherNo?: string
  fromDate?: string
  toDate?: string
  page?: number
  size?: number
}

export interface CreateRevenueDTO {
  contractId: string
  periodMonth: string
  recognizeDate: string
  incomeType?: string
  amount: number
  taxRate?: number
  costAmount?: number
  voucherNo?: string
  remark?: string
}

export interface UpdateRevenueDTO extends CreateRevenueDTO {
  version: number
}

export interface RevenueSummaryVO {
  contractId: string
  contractCode: string
  contractName: string
  contractAmount: number
  confirmedAmount: number
  contractDiff: number
  receivedAmount: number
  plannedUnsettled: number
  balanceDiff: number
  complete: boolean
}

export interface RevenueBundleDTO {
  contractId: string
  revenues: CreateRevenueDTO[]
  plans?: BatchPlanItem[]
  planOverwrite?: boolean
}

export const revenueApi = {
  list: (q: RevenueQuery) => get<PageResult<RevenueRecordVO>>('/revenues', { params: q }),
  detail: (id: string) => get<RevenueRecordVO>(`/revenues/${id}`),
  summary: (contractId: string) => get<RevenueSummaryVO>(`/revenues/summary/${contractId}`),
  create: (dto: CreateRevenueDTO) => post<string>('/revenues', dto),
  bundle: (dto: RevenueBundleDTO) => post<RevenueSummaryVO>('/revenues/bundle', dto),
  update: (id: string, dto: UpdateRevenueDTO) => put<void>(`/revenues/${id}`, dto),
  remove: (id: string) => del<void>(`/revenues/${id}`),
  importExcel: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post<unknown, ImportResultVO>('/revenues/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  downloadTemplate: () =>
    http.get<unknown, Blob>('/revenues/template', { responseType: 'blob' })
}

export const IncomeTypeLabel: Record<string, { label: string; type: 'primary' | 'success' | 'info' }> = {
  GOODS: { label: '商品', type: 'primary' },
  SERVICE: { label: '服务', type: 'success' },
  OTHER: { label: '其他', type: 'info' }
}
