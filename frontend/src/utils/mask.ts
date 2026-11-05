/**
 * 展示层脱敏工具：仅在页面渲染时使用，不改变接口返回与库内数据。
 * 手机号保留前 3 位与后 4 位，邮箱保留 @ 前前 2 位与域名。
 */
export function maskMobile(value?: string | null): string {
  const text = (value ?? '').trim()
  if (!text) return ''
  if (text.length < 7) return `${text.slice(0, 1)}****`
  return `${text.slice(0, 3)}****${text.slice(-4)}`
}

export function maskEmail(value?: string | null): string {
  const text = (value ?? '').trim()
  if (!text) return ''
  const at = text.indexOf('@')
  if (at <= 0) return maskMobile(text)
  const name = text.slice(0, at)
  const domain = text.slice(at)
  const head = name.slice(0, Math.min(2, name.length))
  return `${head}***${domain}`
}
