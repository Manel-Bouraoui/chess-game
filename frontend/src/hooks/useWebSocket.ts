// frontend/src/hooks/useWebSocket.ts
// WebSocket STOMP hook with reliable queue processing

import { useEffect, useRef, useCallback, useState } from 'react';
import { Client, Frame, Message } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ServerMessage } from '@/types/messages';

interface UseWebSocketOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: string) => void;
  autoConnect?: boolean;
  reconnectDelay?: number;
  debug?: boolean;
}

interface Subscription {
  id: string;
  unsubscribe: () => void;
}

type QueuedOperation =
  | {
      type: 'subscribe';
      destination: string;
      handler: (message: ServerMessage) => void;
      subscriptionId?: string;
      resolve: (subscription: Subscription) => void;
    }
  | {
      type: 'send';
      destination: string;
      body: Record<string, unknown>;
      resolve: () => void;
    };

function parseFrame(frameBody: string): ServerMessage {
  try {
    const parsed = JSON.parse(frameBody) as ServerMessage | Record<string, unknown>;
    if (parsed && typeof parsed === 'object' && 'payload' in parsed) {
      return parsed as ServerMessage;
    }
    return { type: 'game-state', payload: parsed, timestamp: Date.now(), source: 'server' } as ServerMessage;
  } catch {
    return { type: 'game-state', payload: {}, timestamp: Date.now(), source: 'server' } as ServerMessage;
  }
}

export function useWebSocket(options: UseWebSocketOptions = {}) {
  const { onConnect, onDisconnect, onError, autoConnect = true, reconnectDelay = 3000, debug = false } = options;

  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, Subscription>>(new Map());
  const operationQueueRef = useRef<Array<QueuedOperation>>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  const [latency] = useState(0);

  // Keep callbacks in refs to avoid stale closures
  const onConnectRef = useRef(onConnect);
  const onDisconnectRef = useRef(onDisconnect);
  const onErrorRef = useRef(onError);
  onConnectRef.current = onConnect;
  onDisconnectRef.current = onDisconnect;
  onErrorRef.current = onError;

  // processQueue as a ref — always current, safe to call from onConnect
  const processQueueRef = useRef<() => void>(() => {});
  processQueueRef.current = () => {
    const queue = [...operationQueueRef.current];
    operationQueueRef.current = [];

    if (debug && queue.length > 0) console.log(`📋 Processing ${queue.length} queued operations`);

    for (const op of queue) {
      if (!clientRef.current?.active) {
        // Re-queue everything if disconnected mid-flush
        operationQueueRef.current.push(...queue.slice(queue.indexOf(op)));
        break;
      }
      try {
        if (op.type === 'subscribe') {
          const id = op.subscriptionId || `sub-${Math.random().toString(36).slice(2)}`;
          const sub = clientRef.current.subscribe(op.destination, (frame: Message) => {
            try { op.handler(parseFrame(frame.body)); } catch {}
          });
          const unsub = () => { sub.unsubscribe(); subscriptionsRef.current.delete(id); };
          subscriptionsRef.current.set(id, { id, unsubscribe: unsub });
          op.resolve({ id, unsubscribe: unsub });
        } else if (op.type === 'send') {
          clientRef.current.publish({ destination: op.destination, body: JSON.stringify(op.body) });
          if (debug) console.log(`📤 Sent queued to ${op.destination}`);
          op.resolve();
        }
      } catch (err) {
        console.error('Error processing queued op:', err);
      }
    }
  };

  const initClient = useCallback(() => {
    if (clientRef.current?.active) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws') as unknown as WebSocket,
      connectHeaders: {},
      reconnectDelay,
      onConnect: (_frame: Frame) => {
        console.log('✅ WebSocket connected');
        setIsConnected(true);
        setReconnectAttempts(0);
        onConnectRef.current?.();
        processQueueRef.current(); // Always latest version via ref
      },
      onDisconnect: (_frame: Frame) => {
        console.log('❌ WebSocket disconnected');
        setIsConnected(false);
        onDisconnectRef.current?.();
      },
      onStompError: (frame: Frame) => {
        const error = frame.body || 'Unknown STOMP error';
        console.error('⚠️ STOMP error:', error);
        onErrorRef.current?.(error);
        setReconnectAttempts(prev => prev + 1);
      },
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
    });

    clientRef.current = client;
    client.activate();
  }, [reconnectDelay]);

  const subscribe = useCallback((
    destination: string,
    handler: (message: ServerMessage) => void,
    subscriptionId?: string
  ): Subscription => {
    const id = subscriptionId || `sub-${Math.random().toString(36).slice(2)}`;

    if (!clientRef.current?.active) {
      // Queue — will execute when onConnect fires
      let resolveFn: (s: Subscription) => void = () => {};
      new Promise<Subscription>(r => { resolveFn = r; });
      operationQueueRef.current.push({ type: 'subscribe', destination, handler, subscriptionId: id, resolve: resolveFn });
      console.log(`📋 Queued subscribe to ${destination}`);
      return {
        id,
        unsubscribe: () => {
          operationQueueRef.current = operationQueueRef.current.filter(
            op => !(op.type === 'subscribe' && op.destination === destination)
          );
          subscriptionsRef.current.get(id)?.unsubscribe();
        },
      };
    }

    // Connected — subscribe immediately
    const sub = clientRef.current.subscribe(destination, (frame: Message) => {
      try { handler(parseFrame(frame.body)); } catch {}
    });
    const unsub = () => { sub.unsubscribe(); subscriptionsRef.current.delete(id); };
    subscriptionsRef.current.set(id, { id, unsubscribe: unsub });
    return { id, unsubscribe: unsub };
  }, []);

  const send = useCallback((destination: string, body: Record<string, unknown>) => {
    if (!clientRef.current?.active) {
      // Queue — will be flushed when onConnect fires
      operationQueueRef.current.push({ type: 'send', destination, body, resolve: () => {} });
      console.log(`📋 Queued send to ${destination}`);
      return;
    }
    try {
      clientRef.current.publish({ destination, body: JSON.stringify(body) });
      if (debug) console.log(`📤 Sent to ${destination}`);
    } catch (error) {
      console.error('Error sending:', error);
      // Re-queue on failure so it retries on next connection
      operationQueueRef.current.push({ type: 'send', destination, body, resolve: () => {} });
    }
  }, [debug]);

  const disconnect = useCallback(() => {
    operationQueueRef.current = [];
    subscriptionsRef.current.forEach(sub => sub.unsubscribe());
    subscriptionsRef.current.clear();
    clientRef.current?.deactivate();
    setIsConnected(false);
  }, []);

  const reconnect = useCallback(() => {
    disconnect();
    setReconnectAttempts(0);
    initClient();
  }, [disconnect, initClient]);

  useEffect(() => {
    if (autoConnect) initClient();
    return () => { disconnect(); };
  }, [autoConnect, initClient, disconnect]);

  return { isConnected, latency, reconnectAttempts, subscribe, send, disconnect, reconnect, client: clientRef.current };
}

export type UseWebSocketReturn = ReturnType<typeof useWebSocket>;
