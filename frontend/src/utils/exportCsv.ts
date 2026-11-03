/**
 * CSV 导出（UTF-8 BOM，Excel 打开不乱码）。
 * 内控域制度/风险/缺陷列表导出复用；默认导出当前筛选结果（调用方传入完整行集）。
 */
export interface CsvColumn<T> {
  title: string
  get: (row: T) => unknown
}

function escapeCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  const text = String(value).replace(/\r?\n/g, ' ')
  if (/[",\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

export function buildCsv<T>(rows: T[], columns: CsvColumn<T>[]): string {
  const header = columns.map((column) => escapeCell(column.title)).join(',')
  const body = rows.map((row) => columns.map((column) => escapeCell(column.get(row))).join(','))
  return `\uFEFF${[header, ...body].join('\r\n')}`
}

/** 生成并触发浏览器下载 */
export function exportCsv<T>(filename: string, rows: T[], columns: CsvColumn<T>[]): void {
  const content = buildCsv(rows, columns)
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename.endsWith('.csv') ? filename : `${filename}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
