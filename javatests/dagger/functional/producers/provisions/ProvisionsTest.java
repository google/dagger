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

package dagger.functional.producers.provisions;

import static com.google.common.truth.Truth.assertThat;

import dagger.functional.producers.provisions.Provisions.Output;
import dagger.functional.producers.provisions.Provisions.TestComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProvisionsTest {

  @Test
  public void provisionsOnlyAreHeldInOneProducer() throws Exception {
    TestComponent component = DaggerProvisions_TestComponent.create();
    Output output = component.output().get();
    assertThat(output.injectedClass1).isSameInstanceAs(output.injectedClass2);
  }
}
