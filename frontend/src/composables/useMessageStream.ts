import { onBeforeUnmount, ref } from 'vue'
import { messageStreamUrl } from '@/api/collab'
import { getToken } from '@/utils/auth'

/**
 * 站内信 SSE 订阅：EventSource 无法携带自定义头，token 走 query。
 * 断线后按 1s/2s/4s… 上限 30s 指数退避重连，失败静默（不向控制台输出，避免验收 console 噪声）。
 */
export function useMessageStream(onMessage: (payload: Record<string, unknown>) => void) {
  const connected = ref(false)
  const unread = ref(0)

  let source: EventSource | null = null
  let reconnectTimer: number | null = null
  let retry = 0
  let stopped = false

  function clearTimer(): void {
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function close(): void {
    if (source) {
      source.onopen = null
      source.onmessage = null
      source.onerror = null
      source.close()
      source = null
    }
    connected.value = false
  }

  function scheduleReconnect(): void {
    if (stopped) return
    clearTimer()
    const delay = Math.min(1000 * 2 ** retry, 30000)
    retry += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect(): void {
    if (stopped) return
    const token = getToken()
    if (!token) {
      scheduleReconnect()
      return
    }
    close()
    try {
      source = new EventSource(messageStreamUrl(token))
    } catch {
      scheduleReconnect()
      return
    }
    source.onopen = () => {
      connected.value = true
      retry = 0
    }
    source.onmessage = (event: MessageEvent<string>) => {
      connected.value = true
      retry = 0
      if (!event.data) return
      if (event.data === 'ping' || event.data.startsWith(':') || event.data.startsWith('ping')) return
      try {
        const payload = JSON.parse(event.data) as Record<string, unknown>
        unread.value += 1
        onMessage(payload)
      } catch {
        // 非 JSON 心跳/保活帧静默忽略
      }
    }
    source.onerror = () => {
      connected.value = false
      close()
      scheduleReconnect()
    }
  }

  function start(): void {
    stopped = false
    retry = 0
    connect()
  }

  function stop(): void {
    stopped = true
    clearTimer()
    close()
  }

  onBeforeUnmount(stop)

  return { connected, unread, start, stop }
}
