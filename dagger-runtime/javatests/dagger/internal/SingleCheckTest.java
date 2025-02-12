/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link SingleCheck}.
 */
@RunWith(JUnit4.class)
public class SingleCheckTest {
  @Test(expected = NullPointerException.class)
  public void create_nullPointerException() {
    SingleCheck.provider(null);
  }

  @Test
  public void get() {
    AtomicInteger integer = new AtomicInteger();
    Provider<Integer> provider = SingleCheck.provider(integer::getAndIncrement);
    assertThat(provider.get()).isEqualTo(0);
    assertThat(provider.get()).isEqualTo(0);
    assertThat(provider.get()).isEqualTo(0);
  }
}
