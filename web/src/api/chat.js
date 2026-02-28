const BASE_URL = '/api/chart'

/**
 * 创建新会话
 * @param {string} [title] - 会话标题
 * @returns {Promise<string>} sessionId
 */
export async function createSession(title) {
  const res = await fetch(`${BASE_URL}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  return res.text()
}

/**
 * 获取指定会话的历史消息
 * @param {string} sessionId
 * @returns {Promise<Array>} 消息列表
 */
export async function getMessages(sessionId) {
  const res = await fetch(`${BASE_URL}/sessions/messages?sessionId=${sessionId}`)
  return res.json()
}

/**
 * 发送消息并接收 SSE 流式回复
 * @param {string} sessionId
 * @param {string} content
 * @param {(chunk: string) => void} onChunk - 每次收到一段文本时的回调
 * @param {() => void} onDone - 流结束时的回调
 * @param {(error: Error) => void} onError - 出错时的回调
 */
export async function sendMessageStream(sessionId, content, { onChunk, onDone, onError, signal }) {
  try {
    const res = await fetch(`${BASE_URL}/messages/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, content }),
      signal,
    })

    if (!res.ok) {
      throw new Error(`HTTP error! status: ${res.status}`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 处理 buffer 残留
        if (buffer.trim()) {
          processSSEBuffer(buffer, onChunk)
        }
        onDone?.()
        break
      }

      buffer += decoder.decode(value, { stream: true })

      // SSE 事件之间以 \n\n 分隔
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const event of events) {
        processSSEBuffer(event, onChunk)
      }
    }
  } catch (err) {
    onError?.(err)
  }
}

/**
 * 解析 SSE 事件块，提取文本内容
 *
 * SSE 规范：一个事件内的多个 data: 行，需要用 \n 拼接还原原始内容。
 * 例如 emitter.send("hello\nworld") 会产生:
 *   data:hello
 *   data:world
 * 解析后应还原为 "hello\nworld"
 *
 * 支持两种数据格式：
 * 1. OpenAI JSON: data: {"choices":[{"delta":{"content":"..."}}]}
 * 2. 纯文本: data:你好
 */
function processSSEBuffer(eventText, onChunk) {
  const lines = eventText.split('\n')
  const dataLines = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      // 取 data: 后面的内容（注意：可能是空字符串，代表换行）
      dataLines.push(line.slice(5))
    }
  }

  if (dataLines.length === 0) return

  // 按 SSE 规范，多个 data: 行用 \n 拼接
  const data = dataLines.join('\n')

  if (!data.trim() || data.trim() === '[DONE]') {
    // 如果整体 trim 后是空的，但原始 data 里有换行，说明是纯换行内容
    if (data.includes('\n') && data.trim() === '') {
      onChunk?.('\n')
    }
    return
  }

  // 尝试解析为 OpenAI 兼容 JSON
  const trimmed = data.trim()
  if (trimmed.startsWith('{')) {
    try {
      const json = JSON.parse(trimmed)
      const delta = json.choices?.[0]?.delta
      if (delta?.content != null) {
        onChunk?.(delta.content)
      }
      return
    } catch {
      // JSON 解析失败，fallback 到纯文本
    }
  }

  // 纯文本模式 (Spring SseEmitter.send(text) 的格式)
  onChunk?.(data)
}
