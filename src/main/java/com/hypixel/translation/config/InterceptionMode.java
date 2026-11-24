package com.hypixel.translation.config;

/**
 * Interception modes for text translation processing.
 */
public enum InterceptionMode {
    /**
     * Non-destructive display/render layer interception (default).
     * Preserves underlying in-memory English structures for auxiliary mod compatibility.
     */
    DISPLAY,

    /**
     * In-place packet-layer interception at network protocol level.
     */
    PACKET,

    /**
     * Interception disabled for this category.
     */
    DISABLED
}
