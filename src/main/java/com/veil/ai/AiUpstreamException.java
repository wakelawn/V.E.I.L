package com.veil.ai;

// NVIDIA NIM was reachable but the request or response failed.
public class AiUpstreamException extends RuntimeException {

    public AiUpstreamException(String message) {
        super(message);
    }

    public AiUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
