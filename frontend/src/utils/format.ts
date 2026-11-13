import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { ApiError } from '@/api/http'

/** 0–1 小数 → 百分比显示（0 值与 null 均安全） */
export function pct(rate: number | null | undefined): string {
  if (rate == null || Number.isNaN(rate)) return '—'
  return `${(rate * 100).toFixed(1)}%`
}

/** 毫秒耗时，可空显示 — */
export function ms(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return '—'
  return `${v} ms`
}

/** 展示用本地时间（无时区后缀） */
export function fmtTime(iso?: string | null): string {
  if (!iso) return '—'
  return dayjs(iso).format('YYYY-MM-DD HH:mm:ss')
}

/** 审计过滤参数用：LocalDateTime 绑定要求 YYYY-MM-DDTHH:mm:ss，禁止带 Z/偏移 */
export function toLocalIso(d: Date | null | undefined): string | undefined {
  if (!d) return undefined
  return dayjs(d).format('YYYY-MM-DDTHH:mm:ss')
}

/** 统一错误提示：message + code + traceId */
export function toastError(e: unknown) {
  if (e instanceof ApiError) {
    ElMessage.error(`${e.message}（${e.code}${e.traceId ? ` traceId: ${e.traceId}` : ''}）`)
  } else if (e instanceof Error) {
    ElMessage.error(e.message)
  } else {
    ElMessage.error('未知错误')
  }
}

/** blob 触发浏览器下载 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
