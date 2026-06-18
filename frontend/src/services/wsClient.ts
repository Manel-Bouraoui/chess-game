import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

/**
 * Create and configure STOMP client
 */
export function createWebSocketClient(
    onConnect: (client: Client) => void,
    onError: (error: string) => void
): Client {
    const client = new Client({
        webSocketFactory: () => {
            const socket = new SockJS(WS_URL);
            return socket as unknown as WebSocket;
        },
        connectHeaders: {
            Authorization: `Bearer ${localStorage.getItem('authToken') || ''}`,
        },
        onConnect: () => {
            console.log('✅ WebSocket connected');
            onConnect(client);
        },
        onDisconnect: (frame) => {
            console.log('❌ WebSocket disconnected');
        },
        onStompError: (frame) => {
            const error = frame.body || 'Unknown error';
            console.error('⚠️ STOMP error:', error);
            onError(error);
        },
        debug: (str) => {
            if (import.meta.env.VITE_DEBUG === 'true') {
                console.log('[STOMP]', str);
            }
        },
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
    });

    return client;
}