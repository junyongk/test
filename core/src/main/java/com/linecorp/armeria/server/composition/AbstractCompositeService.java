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

package com.linecorp.armeria.server.composition;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.linecorp.armeria.common.Request;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.Response;
import com.linecorp.armeria.common.util.SafeCloseable;
import com.linecorp.armeria.server.PathMapped;
import com.linecorp.armeria.server.PathMapping;
import com.linecorp.armeria.server.PathMappings;
import com.linecorp.armeria.server.ResourceNotFoundException;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceCallbackInvoker;
import com.linecorp.armeria.server.ServiceConfig;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.ServiceRequestContextWrapper;

/**
 * A skeletal {@link Service} implementation that enables composing multiple {@link Service}s into one.
 * Extend this class to build your own composite {@link Service}. e.g.
 * <pre>{@code
 * public class MyService extends AbstractCompositeService<HttpRequest, HttpResponse> {
 *     public MyService() {
 *         super(CompositeServiceEntry.ofPrefix("/foo/"), new FooService()),
 *               CompositeServiceEntry.ofPrefix("/bar/"), new BarService()),
 *               CompositeServiceEntry.ofCatchAll(), new OtherService()));
 *     }
 * }
 * }</pre>
 *
 * @param <I> the {@link Request} type
 * @param <O> the {@link Response} type
 *
 * @see AbstractCompositeServiceBuilder
 * @see CompositeServiceEntry
 */
public abstract class AbstractCompositeService<I extends Request, O extends Response> implements Service<I, O> {

    private final List<CompositeServiceEntry<I, O>> services;
    private final PathMappings<Service<I, O>> serviceMapping = new PathMappings<>();

    /**
     * Creates a new instance with the specified {@link CompositeServiceEntry}s.
     */
    @SafeVarargs
    protected AbstractCompositeService(CompositeServiceEntry<I, O>... services) {
        this(Arrays.asList(requireNonNull(services, "services")));
    }

    /**
     * Creates a new instance with the specified {@link CompositeServiceEntry}s.
     */
    protected AbstractCompositeService(Iterable<CompositeServiceEntry<I, O>> services) {
        requireNonNull(services, "services");

        final List<CompositeServiceEntry<I, O>> servicesCopy = new ArrayList<>();
        for (CompositeServiceEntry<I, O> e : services) {
            servicesCopy.add(e);
            serviceMapping.add(e.pathMapping(), e.service());
        }

        this.services = Collections.unmodifiableList(servicesCopy);

        serviceMapping.freeze();
    }

    @Override
    public void serviceAdded(ServiceConfig cfg) throws Exception {
        for (CompositeServiceEntry<I, O> e : services()) {
            ServiceCallbackInvoker.invokeServiceAdded(cfg, e.service());
        }
    }

    /**
     * Returns the list of {@link CompositeServiceEntry}s added to this composite {@link Service}.
     */
    protected List<CompositeServiceEntry<I, O>> services() {
        return services;
    }

    /**
     * Returns the {@code index}-th {@link Service} in this composite {@link Service}. The index of the
     * {@link Service} added first is {@code 0}, and so on.
     */
    @SuppressWarnings("unchecked")
    protected <T extends Service<I, O>> T serviceAt(int index) {
        return (T) services().get(index).service();
    }

    /**
     * Finds the {@link Service} whose {@link PathMapping} matches the {@code path}.
     *
     * @param path an absolute path, as defined in <a href="https://tools.ietf.org/html/rfc3986">RFC3986</a>
     * @param query a query, as defined in <a href="https://tools.ietf.org/html/rfc3986">RFC3986</a>.
     *              {@code null} if query does not exist.
     *
     * @return the {@link Service} wrapped by {@link PathMapped} if there's a match.
     *         {@link PathMapped#empty()} if there's no match.
     */
    protected PathMapped<Service<I, O>> findService(String path, @Nullable String query) {
        return serviceMapping.apply(path, query);
    }

    @Override
    public O serve(ServiceRequestContext ctx, I req) throws Exception {
        final PathMapped<Service<I, O>> mapped = findService(ctx.mappedPath(), ctx.query());
        if (!mapped.isPresent()) {
            throw ResourceNotFoundException.get();
        }

        final Optional<String> childPrefix = mapped.mapping().prefix();
        if (childPrefix.isPresent()) {
            final PathMapping newMapping = PathMapping.ofPrefix(ctx.pathMapping().prefix().get() +
                                                                childPrefix.get().substring(1));

            final ServiceRequestContext newCtx = new CompositeServiceRequestContext(
                    ctx, newMapping, mapped.mappingResult().path());
            try (SafeCloseable ignored = RequestContext.push(newCtx, false)) {
                return mapped.value().serve(newCtx, req);
            }
        } else {
            return mapped.value().serve(ctx, req);
        }
    }

    private static final class CompositeServiceRequestContext extends ServiceRequestContextWrapper {

        private final PathMapping pathMapping;
        private final String mappedPath;

        CompositeServiceRequestContext(ServiceRequestContext delegate, PathMapping pathMapping,
                                       String mappedPath) {
            super(delegate);
            this.pathMapping = pathMapping;
            this.mappedPath = mappedPath;
        }

        @Override
        public PathMapping pathMapping() {
            return pathMapping;
        }

        @Override
        public String mappedPath() {
            return mappedPath;
        }
    }
}
