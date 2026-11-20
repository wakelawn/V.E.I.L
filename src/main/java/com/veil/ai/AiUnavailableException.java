package com.veil.ai;

// NVIDIA NIM cannot be called because no API key is configured.
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
