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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.protos.test.CoffeeService.CoffeeType.AMERICANO;
import static com.google.protos.test.CoffeeService.CoffeeType.DRIP;
import static com.google.protos.test.CoffeeService.CoffeeType.ESPRESSO;
import static com.google.protos.test.CoffeeService.CoffeeType.LATTE;
import static com.google.protos.test.CoffeeService.CoffeeType.POUR_OVER;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protos.test.BaristaGrpc;
import com.google.protos.test.BaristaGrpc.BaristaStub;
import com.google.protos.test.CoffeeService.CoffeeRequest;
import com.google.protos.test.CoffeeService.CoffeeResponse;
import com.google.protos.test.CoffeeService.CoffeeType;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BaristaTest {

  private static final class CoffeeResponseObserver implements StreamObserver<CoffeeResponse> {
    private final SettableFuture<Void> completion = SettableFuture.create();
    private final List<CoffeeResponse> responses = new ArrayList<>();

    List<CoffeeResponse> responses() {
      getUnchecked(completion);
      return responses;
    }

    @Override
    public void onNext(CoffeeResponse value) {
      responses.add(value);
    }

    @Override
    public void onError(Throwable t) {
      completion.setException(t);
    }

    @Override
    public void onCompleted() {
      completion.set(null);
    }
  }

  @ClassRule
  public static CoffeeServerResource coffeeServerWithCallScope =
      new CoffeeServerResource("CallScope", DaggerCoffeeServerWithCallScopeService.builder());

  @ClassRule
  public static CoffeeServerResource coffeeServerWithSingletonScope =
      new CoffeeServerResource("Unscoped", DaggerCoffeeServerWithUnscopedService.builder());

  @Parameters(name = "{0}")
  public static Iterable<Object[]> coffeeServers() {
    return ImmutableList.copyOf(
        new Object[][] {{coffeeServerWithCallScope}, {coffeeServerWithSingletonScope}});
  }

  @Rule public final VerifyInterceptor verifyCount;
  private final CoffeeServerResource coffeeServer;
  private final CoffeeResponseObserver responseObserver = new CoffeeResponseObserver();

  private BaristaStub barista;

  public BaristaTest(CoffeeServerResource coffeeServer) {
    this.coffeeServer = coffeeServer;
    this.verifyCount = new VerifyInterceptor(coffeeServer);
  }

  @Before
  public void setUp() {
    barista = BaristaGrpc.newStub(InProcessChannelBuilder.forName(coffeeServer.name()).build());
  }

  @Test
  public void testUnaryGetCoffee() {
    barista.unaryGetCoffee(request(POUR_OVER, LATTE), responseObserver);
    assertThat(responseObserver.responses())
        .containsExactly(response("Here you go!", POUR_OVER, LATTE));
  }

  @Test
  public void testClientStreamingGetCoffee() {
    StreamObserver<CoffeeRequest> requestObserver =
        barista.clientStreamingGetCoffee(responseObserver);
    requestObserver.onNext(request(POUR_OVER, LATTE));
    requestObserver.onNext(request(AMERICANO));
    requestObserver.onNext(request(DRIP, ESPRESSO));
    requestObserver.onCompleted();
    assertThat(responseObserver.responses())
        .containsExactly(response("All yours!", POUR_OVER, LATTE, AMERICANO, DRIP, ESPRESSO));
  }

  @Test
  public void testServerStreamingGetCoffee() {
    barista.serverStreamingGetCoffee(request(DRIP, AMERICANO), responseObserver);
    assertThat(responseObserver.responses())
        .containsExactly(
            response("Here's a DRIP", DRIP), response("Here's a AMERICANO", AMERICANO));
  }

  @Test
  public void testBidiStreamingGetCoffee() {
    StreamObserver<CoffeeRequest> requestObserver =
        barista.bidiStreamingGetCoffee(responseObserver);
    requestObserver.onNext(request(POUR_OVER, LATTE));
    requestObserver.onNext(request(AMERICANO));
    requestObserver.onNext(request(DRIP, ESPRESSO));
    requestObserver.onCompleted();
    assertThat(responseObserver.responses())
        .containsExactly(
            response("Enjoy!", POUR_OVER, LATTE),
            response("Enjoy!", AMERICANO),
            response("Enjoy!", DRIP, ESPRESSO));
  }

  private CoffeeRequest request(CoffeeType... types) {
    return CoffeeRequest.newBuilder().addAllType(asList(types)).build();
  }

  private CoffeeResponse response(String message, CoffeeType... types) {
    return CoffeeResponse.newBuilder().setMessage(message).addAllCup(asList(types)).build();
  }
}
