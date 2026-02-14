package com.codescope.backend.realtime.websocket;

public enum WebSocketEventType {
    // Job Status Events
    JOB_QUEUED,
    JOB_STARTED,
    JOB_RUNNING,
    JOB_COMPLETED,
    JOB_FAILED,

    // Chat Message Events
    CHAT_USER_MESSAGE,
    CHAT_AI_MESSAGE,

    // System Notifications
    SYSTEM_NOTIFICATION
}
