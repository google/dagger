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

import static java.util.Collections.singletonList;

import com.google.protos.test.BaristaGrpc;
import com.google.protos.test.BaristaGrpc.BaristaImplBase;
import com.google.protos.test.CoffeeService.CoffeeRequest;
import com.google.protos.test.CoffeeService.CoffeeResponse;
import com.google.protos.test.CoffeeService.CoffeeType;
import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import java.util.List;
import javax.inject.Inject;

@GrpcService(grpcClass = BaristaGrpc.class)
class FriendlyBarista extends BaristaImplBase {

  @Inject
  FriendlyBarista() {}

  @Override
  public void unaryGetCoffee(
      CoffeeRequest request, StreamObserver<CoffeeResponse> responseObserver) {
    responseObserver.onNext(response("Here you go!", request.getTypeList()));
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<CoffeeRequest> clientStreamingGetCoffee(
      final StreamObserver<CoffeeResponse> responseObserver) {
    return new StreamObserver<CoffeeRequest>() {

      private final CoffeeResponse.Builder response = CoffeeResponse.newBuilder();

      @Override
      public void onNext(CoffeeRequest value) {
        response.addAllCup(value.getTypeList());
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {
        response.setMessage("All yours!");
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
      }
    };
  }

  @Override
  public void serverStreamingGetCoffee(
      CoffeeRequest request, StreamObserver<CoffeeResponse> responseObserver) {
    for (CoffeeType type : request.getTypeList()) {
      responseObserver.onNext(response("Here's a " + type, singletonList(type)));
    }
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<CoffeeRequest> bidiStreamingGetCoffee(
      final StreamObserver<CoffeeResponse> responseObserver) {
    return new StreamObserver<CoffeeRequest>() {

      private int responses;

      @Override
      public void onNext(CoffeeRequest value) {
        responseObserver.onNext(response("Enjoy!", value.getTypeList()));
        if (responses++ > 10) {
          responseObserver.onNext(CoffeeResponse.newBuilder().setMessage("We're done.").build());
          responseObserver.onCompleted();
        }
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {
        responseObserver.onCompleted();
      }
    };
  }

  private CoffeeResponse response(String message, List<CoffeeType> types) {
    return CoffeeResponse.newBuilder().addAllCup(types).setMessage(message).build();
  }
}
