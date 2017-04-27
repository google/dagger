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

package dagger.grpc.functional.server;

import static java.util.Arrays.asList;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.protos.test.BaristaGrpc;
import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.ForGrpcService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class CountingInterceptor implements ServerInterceptor {
  private final Multiset<String> calls = ConcurrentHashMultiset.create();

  @Inject
  CountingInterceptor() {}

  @Override
  public <RequestT, ResponseT> Listener<RequestT> interceptCall(
      ServerCall<RequestT, ResponseT> call,
      Metadata headers,
      ServerCallHandler<RequestT, ResponseT> next) {
    calls.add(call.getMethodDescriptor().getFullMethodName());
    return next.startCall(call, headers);
  }

  public int countCalls(String methodName) {
    return calls.count(methodName);
  }

  @Module
  static class CountingInterceptorModule {
    @Provides
    @ForGrpcService(BaristaGrpc.class)
    static List<? extends ServerInterceptor> testServiceInterceptors(
        CountingInterceptor countingInterceptor) {
      return asList(countingInterceptor);
    }
  }
}
