package com.lavochk.listTelegram;

import org.bukkit.Bukkit;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Rate limiter for Telegram API calls to prevent 429 (Too Many Requests) errors.
 * Implements a queue-based system with minimum delay between requests and retry logic.
 */
public class TelegramRateLimiter {

    private static final long MIN_REQUEST_INTERVAL_MS = 50; // Minimum 50ms between requests
    private static final long DEFAULT_RETRY_AFTER_MS = 30000; // Default retry after 30 seconds
    private static final int MAX_RETRIES = 3;
    
    private final ListTelegram plugin;
    private final ConcurrentLinkedQueue<QueuedRequest> requestQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicLong retryUntilTime = new AtomicLong(0);
    
    public TelegramRateLimiter(ListTelegram plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Execute a Telegram API request with rate limiting and retry logic.
     * The request is queued and executed asynchronously.
     * 
     * @param request The request to execute
     * @param <T> The return type of the request
     */
    public <T> void executeAsync(Supplier<T> request) {
        executeAsync(request, result -> {}, error -> {});
    }
    
    /**
     * Execute a Telegram API request with rate limiting, retry logic, and callbacks.
     * 
     * @param request The request to execute
     * @param onSuccess Callback for successful execution
     * @param onError Callback for errors (after all retries exhausted)
     * @param <T> The return type of the request
     */
    public <T> void executeAsync(Supplier<T> request, SuccessCallback<T> onSuccess, ErrorCallback onError) {
        QueuedRequest<T> queuedRequest = new QueuedRequest<>(request, onSuccess, onError, 0);
        requestQueue.offer(queuedRequest);
        processQueue();
    }
    
    /**
     * Execute a Telegram API request synchronously with rate limiting.
     * This will block until the request is executed or fails.
     * 
     * @param request The request to execute
     * @return The result of the request, or null if it failed
     * @param <T> The return type of the request
     */
    public <T> T executeSync(Supplier<T> request) {
        waitForRateLimit();
        try {
            return request.get();
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                handleRateLimitError(e);
                // Retry once after waiting
                waitForRetry();
                try {
                    return request.get();
                } catch (Exception retryError) {
                    plugin.getLogger().warning("Telegram API request failed after retry: " + retryError.getMessage());
                    return null;
                }
            }
            plugin.getLogger().warning("Telegram API request failed: " + e.getMessage());
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processQueue() {
        if (!isProcessing.compareAndSet(false, true)) {
            return; // Already processing
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            while (!requestQueue.isEmpty()) {
                // Check if we're in a rate limit cooldown
                long retryUntil = retryUntilTime.get();
                if (System.currentTimeMillis() < retryUntil) {
                    // Schedule continuation after cooldown
                    long delay = retryUntil - System.currentTimeMillis();
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::processQueue, delay / 50 + 1);
                    isProcessing.set(false);
                    return;
                }
                
                QueuedRequest<Object> request = (QueuedRequest<Object>) requestQueue.poll();
                if (request == null) break;
                
                waitForRateLimit();
                
                try {
                    Object result = request.request.get();
                    request.onSuccess.onSuccess(result);
                } catch (Exception e) {
                    if (isRateLimitError(e)) {
                        handleRateLimitError(e);
                        
                        // Retry if we haven't exceeded max retries
                        if (request.retryCount < MAX_RETRIES) {
                            QueuedRequest<Object> retryRequest = new QueuedRequest<>(
                                request.request, request.onSuccess, request.onError, request.retryCount + 1
                            );
                            requestQueue.offer(retryRequest);
                            
                            // Schedule continuation after cooldown
                            long delay = DEFAULT_RETRY_AFTER_MS / 50 + 1; // Convert to ticks
                            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::processQueue, delay);
                            isProcessing.set(false);
                            return;
                        }
                    }
                    request.onError.onError(e);
                }
                
                lastRequestTime.set(System.currentTimeMillis());
            }
            isProcessing.set(false);
        });
    }
    
    private void waitForRateLimit() {
        long timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void waitForRetry() {
        long retryUntil = retryUntilTime.get();
        long now = System.currentTimeMillis();
        if (now < retryUntil) {
            try {
                Thread.sleep(retryUntil - now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private boolean isRateLimitError(Exception e) {
        if (e instanceof TelegramApiException) {
            String message = e.getMessage();
            return message != null && (message.contains("429") || message.contains("Too Many Requests"));
        }
        return false;
    }
    
    private void handleRateLimitError(Exception e) {
        String message = e.getMessage();
        long retryAfter = DEFAULT_RETRY_AFTER_MS;
        
        // Try to extract retry-after value from error message
        if (message != null && message.contains("retry after")) {
            try {
                String[] parts = message.split("retry after");
                if (parts.length > 1) {
                    String secondsStr = parts[1].trim().split("[^0-9]")[0];
                    retryAfter = Long.parseLong(secondsStr) * 1000;
                }
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }
        
        retryUntilTime.set(System.currentTimeMillis() + retryAfter);
        plugin.getLogger().warning("Telegram rate limit hit. Waiting " + (retryAfter / 1000) + " seconds before retry.");
    }
    
    @FunctionalInterface
    public interface SuccessCallback<T> {
        void onSuccess(T result);
    }
    
    @FunctionalInterface
    public interface ErrorCallback {
        void onError(Exception e);
    }
    
    private static class QueuedRequest<T> {
        final Supplier<T> request;
        final SuccessCallback<T> onSuccess;
        final ErrorCallback onError;
        final int retryCount;
        
        QueuedRequest(Supplier<T> request, SuccessCallback<T> onSuccess, ErrorCallback onError, int retryCount) {
            this.request = request;
            this.onSuccess = onSuccess;
            this.onError = onError;
            this.retryCount = retryCount;
        }
    }
}
