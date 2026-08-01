import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
// SockJS doesn't have bundled types — declare inline
// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const SockJS: any
import 'sockjs-client'

/**
 * Phase 2 — WebSocket live balance updates.
 * Connects to /ws (SockJS), subscribes to /topic/group/{groupId}/balances,
 * calls onMessage with the server push payload.
 * Returns { connected } so callers can show a LIVE indicator.
 */
export function useGroupWebSocket(
  groupId: string | undefined,
  onMessage: (payload: unknown) => void
): { connected: boolean } {
  const clientRef = useRef<Client | null>(null)
  const onMessageRef = useRef(onMessage)
  const [connected, setConnected] = useState(false)
  onMessageRef.current = onMessage

  useEffect(() => {
    if (!groupId) return

    const client = new Client({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      webSocketFactory: () => new (window as any).SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/group/${groupId}/balances`, (msg) => {
          try {
            onMessageRef.current(JSON.parse(msg.body))
          } catch { /* ignore parse errors */ }
        })
      },
      onDisconnect: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate(); setConnected(false) }
  }, [groupId])

  return { connected }
}
