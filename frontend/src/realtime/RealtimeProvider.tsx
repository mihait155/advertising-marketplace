import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { refreshSession, session } from '../api/client';
import type { ConversationEvent } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const TYPING_VISIBLE_MS = 4000;
const TYPING_SEND_INTERVAL_MS = 2500;

interface RealtimeContextValue {
  connected: boolean;
  typingIn: (conversationId: string) => boolean;
  sendTyping: (conversationId: string) => void;
}

const RealtimeContext = createContext<RealtimeContextValue>({
  connected: false,
  typingIn: () => false,
  sendTyping: () => undefined,
});

function brokerUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);
  const lastTypingSent = useRef(new Map<string, number>());
  const [connected, setConnected] = useState(false);
  const [typing, setTyping] = useState<Record<string, number>>({});

  const userId = state.status === 'authenticated' ? state.user.id : null;

  useEffect(() => {
    if (!userId) {
      return;
    }
    let connectedBefore = false;
    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      beforeConnect: async () => {
        if (!session.accessToken) {
          await refreshSession();
        }
        client.connectHeaders = { Authorization: `Bearer ${session.accessToken ?? ''}` };
      },
      onConnect: () => {
        setConnected(true);
        if (connectedBefore) {
          queryClient.invalidateQueries({ queryKey: ['conversations'] });
          queryClient.invalidateQueries({ queryKey: ['conversation'] });
          queryClient.invalidateQueries({ queryKey: ['messages'] });
          queryClient.invalidateQueries({ queryKey: ['unread'] });
        }
        connectedBefore = true;
        client.subscribe('/user/queue/conversation-events', (frame) => {
          const event = JSON.parse(frame.body) as ConversationEvent;
          if (event.type === 'TYPING') {
            setTyping((current) => ({ ...current, [event.conversationId]: Date.now() + TYPING_VISIBLE_MS }));
            return;
          }
          if (event.type === 'MESSAGE_CREATED' && event.actorId !== userId) {
            setTyping((current) => ({ ...current, [event.conversationId]: 0 }));
          }
          queryClient.invalidateQueries({ queryKey: ['messages', event.conversationId] });
          queryClient.invalidateQueries({ queryKey: ['conversations'] });
          queryClient.invalidateQueries({ queryKey: ['conversation', event.conversationId] });
          queryClient.invalidateQueries({ queryKey: ['unread'] });
          if (event.message?.type === 'SYSTEM') {
            queryClient.invalidateQueries({ queryKey: ['offers'] });
            queryClient.invalidateQueries({ queryKey: ['posting'] });
            queryClient.invalidateQueries({ queryKey: ['postings'] });
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => {
        refreshSession();
      },
    });
    clientRef.current = client;
    client.activate();
    return () => {
      clientRef.current = null;
      setConnected(false);
      client.deactivate();
    };
  }, [userId, queryClient]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setTyping((current) => {
        const now = Date.now();
        const next = Object.fromEntries(Object.entries(current).filter(([, until]) => until > now));
        return Object.keys(next).length === Object.keys(current).length ? current : next;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  const typingIn = useCallback((conversationId: string) => (typing[conversationId] ?? 0) > Date.now(), [typing]);

  const sendTyping = useCallback((conversationId: string) => {
    const client = clientRef.current;
    const last = lastTypingSent.current.get(conversationId) ?? 0;
    if (!client?.connected || Date.now() - last < TYPING_SEND_INTERVAL_MS) {
      return;
    }
    lastTypingSent.current.set(conversationId, Date.now());
    client.publish({ destination: `/app/conversations/${conversationId}/typing`, body: '{}' });
  }, []);

  return <RealtimeContext.Provider value={{ connected, typingIn, sendTyping }}>{children}</RealtimeContext.Provider>;
}

export function useRealtime() {
  return useContext(RealtimeContext);
}
