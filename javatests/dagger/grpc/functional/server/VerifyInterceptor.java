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

import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.protos.test.BaristaGrpc;
import io.grpc.MethodDescriptor;
import java.lang.annotation.Retention;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

final class VerifyInterceptor implements TestRule {

  @Retention(RUNTIME)
  @interface MethodName {
    String value();
  }

  private final CoffeeServerResource coffeeServer;

  VerifyInterceptor(CoffeeServerResource coffeeServer) {
    this.coffeeServer = coffeeServer;
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    MethodName annotation = description.getAnnotation(MethodName.class);
    if (annotation == null) {
      return base;
    }
    final String fullMethodName =
        MethodDescriptor.generateFullMethodName(BaristaGrpc.SERVICE_NAME, annotation.value());
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        int calls = coffeeServer.methodCount(fullMethodName);
        base.evaluate();
        assertWithMessage("Calls to %s", fullMethodName)
            .that(coffeeServer.methodCount(fullMethodName))
            .isEqualTo(calls + 1);
      }
    };
  }
}
