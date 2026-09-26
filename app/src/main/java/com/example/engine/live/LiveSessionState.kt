package com.example.engine.live

/**
 * Lifecycle states for the Gemini Multimodal Live API Voice Engine.
 */
enum class LiveSessionState {
    /**
     * Idle mode: WebSocket is disconnected to conserve battery and bandwidth.
     * Lightweight offline wake-word detector is actively waiting for "Hello".
     */
    IDLE_LISTENING_WAKEWORD,

    /**
     * Transition state: Wake-word detected or manual start triggered.
     * Establishing bi-directional WebSocket and sending initial configuration.
     */
    CONNECTING,

    /**
     * Active state: Bi-directional audio streaming active.
     * Continuous 16kHz PCM audio streaming, real-time incoming AI audio,
     * AEC and instant barge-in enabled. 10-minute session timer ticking.
     */
    LIVE_ACTIVE,

    /**
     * Graceful teardown state: closing WebSocket, releasing audio buffers,
     * flushing playback, returning to wake-word idle mode.
     */
    DISCONNECTING,

    /**
     * Error state: network dropout, socket failure, or mic permission issue.
     */
    ERROR
}
