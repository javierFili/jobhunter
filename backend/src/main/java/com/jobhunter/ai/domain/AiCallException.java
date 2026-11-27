package com.jobhunter.ai.domain;

/** Provider error classified as retryable (429/5xx/timeouts) or not (400/401/402/422...). */
public class AiCallException extends RuntimeException {

    private final boolean retryable;
    private final int status;

    public AiCallException(String message, int status, boolean retryable, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
    public int status() { return status; }

    public static boolean isRetryableStatus(int status) {
        return status == 0 || status == 408 || status == 429 || status == 500 || status == 502 || status == 503
                || status == 504 || status == 529;
    }
}
