import type { ConversationSession } from '@/types'

/** Export a session as a Markdown string and trigger download */
export function exportAsMarkdown(session: ConversationSession): void {
  const lines: string[] = [
    `# ${session.title}`,
    ``,
    `> 城市：${session.cities.join('、') || '未选择'}`,
    `> 时间：${new Date(session.createdAt).toLocaleString('zh-CN')}`,
    ``,
    `---`,
    ``,
  ]

  for (const msg of session.messages) {
    if (msg.loading) continue
    const role = msg.role === 'user' ? '## 我' : '## 旅途 AI'
    const time = new Date(msg.timestamp).toLocaleTimeString('zh-CN', {
      hour: '2-digit', minute: '2-digit',
    })
    lines.push(`${role}  *(${time})*`)
    lines.push(``)
    lines.push(msg.content)
    lines.push(``)

    if (msg.sources && msg.sources.length > 0) {
      lines.push(`> **参考来源：**${msg.sources.map(s => `${s.source}`).join('、')}`)
      lines.push(``)
    }

    lines.push(`---`)
    lines.push(``)
  }

  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${session.title.replace(/[\/\\:*?"<>|]/g, '_')}.md`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** Print current chat as formatted page */
export function printChat(session: ConversationSession): void {
  const win = window.open('', '_blank')
  if (!win) return

  const rows = session.messages
    .filter(m => !m.loading)
    .map(m => {
      const isUser = m.role === 'user'
      const label = isUser ? '我' : '旅途 AI'
      const time = new Date(m.timestamp).toLocaleTimeString('zh-CN', {
        hour: '2-digit', minute: '2-digit',
      })
      const content = m.content.replace(/\n/g, '<br>')
      return `
        <div class="msg ${isUser ? 'user' : 'ai'}">
          <div class="meta">${label} · ${time}</div>
          <div class="content">${content}</div>
        </div>`
    }).join('')

  win.document.write(`
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
      <meta charset="UTF-8" />
      <title>${session.title}</title>
      <style>
        body { font-family: 'PingFang SC','Microsoft YaHei',sans-serif; max-width: 700px; margin: 40px auto; color: #1a1a1a; }
        h1 { font-size: 22px; margin-bottom: 4px; }
        .meta-top { font-size: 13px; color: #888; margin-bottom: 24px; }
        .msg { margin-bottom: 20px; padding: 14px 18px; border-radius: 10px; }
        .msg.user { background: #f5e8eb; }
        .msg.ai { background: #faf6f0; border: 1px solid #dbc8ce; }
        .meta { font-size: 11px; color: #999; margin-bottom: 6px; font-weight: 600; }
        .content { font-size: 14px; line-height: 1.7; }
      </style>
    </head>
    <body>
      <h1>${session.title}</h1>
      <div class="meta-top">城市：${session.cities.join('、') || '未选择'} · ${new Date(session.createdAt).toLocaleString('zh-CN')}</div>
      ${rows}
      <script>window.onload = function(){ window.print(); }<\/script>
    </body>
    </html>`)
  win.document.close()
}
