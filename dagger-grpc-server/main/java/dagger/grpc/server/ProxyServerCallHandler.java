/*
 * Copyright (C) 2016 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.grpc.server;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import java.io.InputStream;

/**
 * A {@link ServerCallHandler} that handles calls for a particular method by delegating to a handler
 * in a {@link ServerServiceDefinition} returned by a factory.
 *
 * @param <RequestT> the type of the request payloads
 * @param <ResponseT> the type of the response payloads
 */
public final class ProxyServerCallHandler<RequestT, ResponseT>
    implements ServerCallHandler<InputStream, InputStream> {

  /**
   * A factory for the {@link ServerServiceDefinition} that a {@link ProxyServerCallHandler}
   * delegates to.
   */
  public interface ServiceDefinitionFactory {
    /**
     * Returns a service definition that contains a {@link ServerCallHandler} for the
     * {@link ProxyServerCallHandler}'s method.
     */
    ServerServiceDefinition getServiceDefinition(Metadata headers);
  }

  private final MethodDescriptor<RequestT, ResponseT> delegateMethodDescriptor;
  private final ServiceDefinitionFactory delegateServiceDefinitionFactory;

  /**
   * Returns a proxy method definition for {@code methodDescriptor}.
   *
   * @param delegateServiceDefinitionFactory factory for the delegate service definition
   */
  public static <RequestT, ResponseT> ServerMethodDefinition<InputStream, InputStream> proxyMethod(
      MethodDescriptor<RequestT, ResponseT> delegateMethodDescriptor,
      ServiceDefinitionFactory delegateServiceDefinitionFactory) {
    return ServerMethodDefinition.create(
        MethodDescriptor.create(
            delegateMethodDescriptor.getType(),
            delegateMethodDescriptor.getFullMethodName(),
            IDENTITY_MARSHALLER,
            IDENTITY_MARSHALLER),
        new ProxyServerCallHandler<>(delegateMethodDescriptor, delegateServiceDefinitionFactory));
  }

  ProxyServerCallHandler(
      MethodDescriptor<RequestT, ResponseT> delegateMethodDescriptor,
      ServiceDefinitionFactory delegateServiceDefinitionFactory) {
    this.delegateMethodDescriptor = delegateMethodDescriptor;
    this.delegateServiceDefinitionFactory = delegateServiceDefinitionFactory;
  }

  @Override
  public Listener<InputStream> startCall(
      ServerCall<InputStream, InputStream> call,
      Metadata headers) {
    ServerMethodDefinition<RequestT, ResponseT> delegateMethod = getMethodDefinition(headers);
    Listener<RequestT> delegateListener =
        delegateMethod
            .getServerCallHandler()
            .startCall(new ServerCallAdapter(call, delegateMethod.getMethodDescriptor()), headers);
    return new ServerCallListenerAdapter(delegateListener);
  }

  @SuppressWarnings("unchecked") // Method definition is the correct type.
  private ServerMethodDefinition<RequestT, ResponseT> getMethodDefinition(Metadata headers) {
    String fullMethodName = delegateMethodDescriptor.getFullMethodName();
    for (ServerMethodDefinition<?, ?> methodDefinition :
        delegateServiceDefinitionFactory.getServiceDefinition(headers).getMethods()) {
      if (methodDefinition.getMethodDescriptor().getFullMethodName().equals(fullMethodName)) {
        return (ServerMethodDefinition<RequestT, ResponseT>) methodDefinition;
      }
    }
    throw new IllegalStateException("Could not find " + fullMethodName);
  }

  private static final Marshaller<InputStream> IDENTITY_MARSHALLER =
      new Marshaller<InputStream>() {
        @Override
        public InputStream stream(InputStream value) {
          return value;
        }

        @Override
        public InputStream parse(InputStream stream) {
          return stream;
        }
      };

  /** A {@link Listener} that adapts {@code Listener<RequestT>} to {@code Listener<InputStream>}. */
  private final class ServerCallListenerAdapter extends Listener<InputStream> {

    private final Listener<RequestT> delegate;

    public ServerCallListenerAdapter(Listener<RequestT> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onMessage(InputStream message) {
      delegate.onMessage(delegateMethodDescriptor.parseRequest(message));
    }

    @Override
    public void onHalfClose() {
      delegate.onHalfClose();
    }

    @Override
    public void onCancel() {
      delegate.onCancel();
    }

    @Override
    public void onComplete() {
      delegate.onComplete();
    }
  }

  /**
   * A {@link ServerCall} that adapts {@code ServerCall<InputStream>} to {@code
   * ServerCall<ResponseT>}.
   */
  final class ServerCallAdapter extends ServerCall<RequestT, ResponseT> {

    private final ServerCall<InputStream, InputStream> delegate;
    private final MethodDescriptor<RequestT, ResponseT> method;

    ServerCallAdapter(ServerCall<InputStream, InputStream> delegate,
        MethodDescriptor<RequestT, ResponseT> method) {
      this.delegate = delegate;
      this.method = method;
    }

    @Override
    public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
      return method;
    }

    @Override
    public void request(int numMessages) {
      delegate.request(numMessages);
    }

    @Override
    public void sendHeaders(Metadata headers) {
      delegate.sendHeaders(headers);
    }

    @Override
    public void sendMessage(ResponseT message) {
      delegate.sendMessage(delegateMethodDescriptor.streamResponse(message));
    }

    @Override
    public void close(Status status, Metadata trailers) {
      delegate.close(status, trailers);
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }
  }
}
