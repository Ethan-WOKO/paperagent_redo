package com.yanban.api.ws;

import com.yanban.api.agent.AgentDebugPayload;

public record WsChatEvent(
        String type,
        String content,
        Long sessionId,
        String error,
        String finishReason,
        String navigationUrl,
        String clientRequestId,
        AgentDebugPayload debug
) {
    public static WsChatEvent ack(Long sessionId, String clientRequestId) {
        return new WsChatEvent("ack", null, sessionId, null, null, null, clientRequestId, null);
    }

    public static WsChatEvent chunk(Long sessionId, String content, String clientRequestId) {
        return new WsChatEvent("chunk", content, sessionId, null, null, null, clientRequestId, null);
    }

    public static WsChatEvent process(Long sessionId, String content, String clientRequestId) {
        return new WsChatEvent("process", content, sessionId, null, null, null, clientRequestId, null);
    }

    public static WsChatEvent done(Long sessionId, String finishReason, String clientRequestId) {
        return new WsChatEvent("done", null, sessionId, null, finishReason, null, clientRequestId, null);
    }

    public static WsChatEvent doneWithNavigation(Long sessionId, String finishReason, String navigationUrl, String clientRequestId) {
        return new WsChatEvent("done", null, sessionId, null, finishReason, navigationUrl, clientRequestId, null);
    }

    public static WsChatEvent error(Long sessionId, String error, String clientRequestId) {
        return new WsChatEvent("error", null, sessionId, error, null, null, clientRequestId, null);
    }

    public static WsChatEvent debug(Long sessionId, AgentDebugPayload debug, String clientRequestId) {
        return new WsChatEvent("debug", null, sessionId, null, null, null, clientRequestId, debug);
    }
}
