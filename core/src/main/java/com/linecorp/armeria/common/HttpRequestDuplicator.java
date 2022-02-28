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

package com.linecorp.armeria.common;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;

import com.linecorp.armeria.common.stream.AbstractStreamMessageDuplicator;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.common.stream.StreamMessageWrapper;

/**
 * Allows subscribing to a {@link HttpRequest} multiple times by duplicating the stream.
 *
 * <pre>{@code
 * final HttpRequest originalReq = ...
 * final HttpRequestDuplicator reqDuplicator = new HttpRequestDuplicator(originalReq);
 *
 * final HttpRequest dupReq1 = reqDuplicator.duplicateStream();
 * final HttpRequest dupReq2 = reqDuplicator.duplicateStream();
 *
 * dupReq1.subscribe(new FooSubscriber() {
 *     ...
 *     // Do something according to the first few elements of the request.
 * });
 *
 * final CompletableFuture<AggregatedHttpMessage> future2 = dupReq2.aggregate();
 * future2.handle((message, cause) -> {
 *     // Do something with message.
 * }
 * }</pre>
 */
public class HttpRequestDuplicator extends AbstractStreamMessageDuplicator<HttpObject, HttpRequest> {

    private final HttpHeaders headers;

    private final boolean keepAlive;

    /**
     * Creates a new instance wrapping a {@link HttpRequest} and publishing to multiple subscribers.
     * @param req the request that will publish data to subscribers
     */
    public HttpRequestDuplicator(HttpRequest req) {
        super(requireNonNull(req, "req"));
        headers = req.headers();
        keepAlive = req.isKeepAlive();
    }

    @Override
    protected HttpRequest doDuplicateStream(StreamMessage<HttpObject> delegate) {
        return new DuplicateHttpRequest(delegate);
    }

    private class DuplicateHttpRequest
            extends StreamMessageWrapper<HttpObject> implements HttpRequest {

        DuplicateHttpRequest(StreamMessage<? extends HttpObject> delegate) {
            super(delegate);
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public boolean isKeepAlive() {
            return keepAlive;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                              .add("keepAlive", isKeepAlive())
                              .add("headers", headers()).toString();
        }
    }
}
