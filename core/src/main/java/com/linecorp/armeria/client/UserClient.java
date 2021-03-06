/*
 * Copyright 2016 LINE Corporation
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

package com.linecorp.armeria.client;

import java.net.URI;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.linecorp.armeria.common.DefaultHttpHeaders;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.Request;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.Response;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.SafeCloseable;

import io.netty.channel.EventLoop;
import io.netty.util.Attribute;

/**
 * A base class for implementing a user's entry point for sending a {@link Request}.
 *
 * <p>It provides the utility methods for easily forwarding a {@link Request} from a user to a {@link Client}.
 *
 * <p>Note that this class is not a subtype of {@link Client}, although its name may mislead.
 *
 * @param <I> the request type
 * @param <O> the response type
 */
public abstract class UserClient<I extends Request, O extends Response> implements ClientBuilderParams {

    static final ThreadLocal<Function<HttpHeaders, HttpHeaders>> THREAD_LOCAL_HEADER_MANIPULATOR =
            new ThreadLocal<>();

    private final ClientBuilderParams params;
    private final Client<I, O> delegate;
    private final SessionProtocol sessionProtocol;
    private final Endpoint endpoint;

    /**
     * Creates a new instance.
     *
     * @param params the parameters used for constructing the client
     * @param delegate the {@link Client} that will process {@link Request}s
     * @param sessionProtocol the {@link SessionProtocol} of the {@link Client}
     * @param endpoint the {@link Endpoint} of the {@link Client}
     */
    protected UserClient(ClientBuilderParams params,
                         Client<I, O> delegate, SessionProtocol sessionProtocol, Endpoint endpoint) {
        this.params = params;
        this.delegate = delegate;
        this.sessionProtocol = sessionProtocol;
        this.endpoint = endpoint;
    }

    @Override
    public ClientFactory factory() {
        return params.factory();
    }

    @Override
    public URI uri() {
        return params.uri();
    }

    @Override
    public Class<?> clientType() {
        return params.clientType();
    }

    @Override
    public final ClientOptions options() {
        return params.options();
    }

    /**
     * Returns the {@link Client} that will process {@link Request}s.
     */
    @SuppressWarnings("unchecked")
    protected final <U extends Client<I, O>> U delegate() {
        return (U) delegate;
    }

    /**
     * Retrieves an {@link EventLoop} supplied by the {@link ClientFactory}.
     */
    protected final EventLoop eventLoop() {
        return params.factory().eventLoopSupplier().get();
    }

    /**
     * Returns the {@link SessionProtocol} of the {@link #delegate()}.
     */
    protected final SessionProtocol sessionProtocol() {
        return sessionProtocol;
    }

    /**
     * Returns the {@link Endpoint} of the {@link #delegate()}.
     */
    protected final Endpoint endpoint() {
        return endpoint;
    }

    /**
     * Executes the specified {@link Request} via {@link #delegate()}.
     *
     * @param method the method of the {@link Request}
     * @param path the path part of the {@link Request} URI
     * @param query the query part of the {@link Request} URI
     * @param fragment the fragment part of the {@link Request} URI
     * @param req the {@link Request}
     * @param fallback the fallback response {@link Function} to use when
     *                 {@link Client#execute(ClientRequestContext, Request)} of {@link #delegate()} throws
     *                 an exception instead of returning an error response
     */
    protected final O execute(HttpMethod method, String path, @Nullable String query, @Nullable String fragment,
                              I req, Function<Throwable, O> fallback) {
        return execute(eventLoop(), method, path, query, fragment, req, fallback);
    }

    /**
     * Executes the specified {@link Request} via {@link #delegate()}.
     *
     * @param eventLoop the {@link EventLoop} to execute the {@link Request}
     * @param method the method of the {@link Request}
     * @param path the path part of the {@link Request} URI
     * @param query the query part of the {@link Request} URI
     * @param fragment the fragment part of the {@link Request} URI
     * @param req the {@link Request}
     * @param fallback the fallback response {@link Function} to use when
     *                 {@link Client#execute(ClientRequestContext, Request)} of {@link #delegate()} throws
     */
    protected final O execute(EventLoop eventLoop,
                              HttpMethod method, String path, @Nullable String query, @Nullable String fragment,
                              I req, Function<Throwable, O> fallback) {

        final ClientRequestContext ctx = new DefaultClientRequestContext(
                eventLoop, sessionProtocol, endpoint, method, path, query, fragment, options(), req);

        try (SafeCloseable ignored = RequestContext.push(ctx)) {
            runThreadLocalHeaderManipulator(ctx);
            return delegate().execute(ctx, req);
        } catch (Throwable cause) {
            ctx.logBuilder().endResponse(cause);
            return fallback.apply(cause);
        }
    }

    private static void runThreadLocalHeaderManipulator(ClientRequestContext ctx) {
        final Function<HttpHeaders, HttpHeaders> manipulator = THREAD_LOCAL_HEADER_MANIPULATOR.get();
        if (manipulator == null) {
            return;
        }

        final Attribute<HttpHeaders> attr = ctx.attr(ClientRequestContext.HTTP_HEADERS);
        final HttpHeaders headers = attr.get();
        attr.set(manipulator.apply(headers != null ? headers : new DefaultHttpHeaders()));
    }
}
