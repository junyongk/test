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

package com.linecorp.armeria.internal.grpc;

import com.linecorp.armeria.common.RpcRequest;
import com.linecorp.armeria.common.RpcResponse;

import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * Utilities shared between server/client related to logging.
 */
public final class GrpcLogUtil {

    /**
     * Returns a {@link RpcRequest} corresponding to the given {@link MethodDescriptor}.
     */
    public static RpcRequest rpcRequest(MethodDescriptor<?, ?> method) {
        // We don't actually use the RpcRequest for request processing since it doesn't fit well with streaming.
        // We still populate it with a reasonable method name for use in logging. The service type is currently
        // arbitrarily set as gRPC doesn't use Class<?> to represent services - if this becomes a problem, we
        // would need to refactor it to take a Object instead.
        return RpcRequest.of(GrpcLogUtil.class, method.getFullMethodName());
    }

    /**
     * Returns a {@link RpcResponse} corresponding to the given {@link Status} generated by the server.
     */
    public static RpcResponse rpcResponse(Status status) {
        if (status.isOk()) {
            // gRPC responses are streamed so we don't have anything to set as the RpcResponse, so we set it
            // arbitrarily so it can at least be counted.
            return RpcResponse.of("success");
        } else {
            return RpcResponse.ofFailure(status.asException());
        }
    }

    private GrpcLogUtil() {}
}
