/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.common.stream;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.reactivestreams.Subscriber;

import com.google.common.base.MoreObjects;

/**
 * Wraps a {@link StreamMessage} and forwards its method invocations to {@code delegate}.
 * @param <T> the type of elements
 */
public class StreamMessageWrapper<T> implements StreamMessage<T> {

    private final StreamMessage<? extends T> delegate;

    /**
     * Creates a new instance that wraps a {@code delegate}.
     */
    public StreamMessageWrapper(StreamMessage<? extends T> delegate) {
        requireNonNull(delegate, "delegate");
        this.delegate = delegate;
    }

    /**
     * Returns the {@link StreamMessage} being decorated.
     */
    protected final StreamMessage<? extends T> delegate() {
        return delegate;
    }

    @Override
    public boolean isOpen() {
        return delegate().isOpen();
    }

    @Override
    public boolean isEmpty() {
        return delegate().isEmpty();
    }

    @Override
    public CompletableFuture<Void> closeFuture() {
        return delegate().closeFuture();
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        delegate().subscribe(s);
    }

    @Override
    public void subscribe(Subscriber<? super T> s, boolean withPooledObjects) {
        delegate().subscribe(s, withPooledObjects);
    }

    @Override
    public void subscribe(Subscriber<? super T> s, Executor executor) {
        delegate().subscribe(s, executor);
    }

    @Override
    public void subscribe(Subscriber<? super T> s, Executor executor, boolean withPooledObjects) {
        delegate().subscribe(s, executor, withPooledObjects);
    }

    @Override
    public void abort() {
        delegate().abort();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("delegate", delegate()).toString();
    }
}
