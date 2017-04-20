/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.producers.monitoring;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.monitoring.StubModule.ForServer1;
import dagger.functional.producers.monitoring.StubModule.ForServer2;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import javax.inject.Qualifier;

@ProducerModule
final class ServingModule {
  @Qualifier
  @interface RequestData {}

  @Qualifier
  @interface IntermediateData {}

  @Produces
  @RequestData
  static String requestData() {
    return "Hello, World!";
  }

  @Produces
  @IntermediateData
  static ListenableFuture<String> callServer1(
      @RequestData String data, @ForServer1 StringStub stub) {
    return stub.run(data);
  }

  @Produces
  static ListenableFuture<String> callServer2(
      @IntermediateData String data, @ForServer2 StringStub stub) {
    return stub.run(data);
  }
}
