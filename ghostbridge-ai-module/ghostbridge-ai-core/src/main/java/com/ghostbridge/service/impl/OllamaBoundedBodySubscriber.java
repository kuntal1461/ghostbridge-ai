package com.ghostbridge.service.impl;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Collects a response body while enforcing a hard byte limit: the subscription is cancelled (closing the connection)
 * as soon as the limit would be exceeded, so an oversized or endless body is never buffered. A declared
 * {@code Content-Length} above the limit is rejected before any body byte is read.
 */
final class OllamaBoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    /** Marker failure; deliberately carries no message or provider data. */
    static final class BodyTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BodyTooLargeException() {
            super(null, null, false, false);
        }
    }

    private final long maxBytes;
    private final long declaredLength;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    /** @param declaredLength the {@code Content-Length} header value, or a negative number when absent */
    OllamaBoundedBodySubscriber(long maxBytes, long declaredLength) {
        this.maxBytes = maxBytes;
        this.declaredLength = declaredLength;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (declaredLength > maxBytes) {
            reject();
            return;
        }
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
        for (ByteBuffer item : items) {
            if (buffer.size() + (long) item.remaining() > maxBytes) {
                reject();
                return;
            }
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            buffer.write(chunk, 0, chunk.length);
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        result.complete(buffer.toByteArray());
    }

    private void reject() {
        subscription.cancel();
        result.completeExceptionally(new BodyTooLargeException());
    }
}
