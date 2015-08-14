/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal;

import javax.inject.Provider;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static org.junit.Assert.fail;

/**
 * Tests {@link ScopedProvider}.
 */
@RunWith(JUnit4.class)
public class ScopedProviderTest {
  @Test public void create_nullPointerException() {
    try {
      ScopedProvider.create(null);
      fail();
    } catch (NullPointerException expected) { }
  }

  // TODO(gak): reenable this test once we can ensure that factories are no longer providing null
  @Ignore @Test public void get_nullPointerException() {
    Provider<Object> scopedProvider = ScopedProvider.create(new Factory<Object>() {
      @Override public Object get() {
        return null;
      }
    });
    try {
      scopedProvider.get();
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void get() {
    Provider<Integer> scopedProvider = ScopedProvider.create(new Factory<Integer>() {
      int i = 0;

      @Override public Integer get() {
        return i++;
      }
    });
    assert_().that(scopedProvider.get()).isEqualTo(0);
    assert_().that(scopedProvider.get()).isEqualTo(0);
    assert_().that(scopedProvider.get()).isEqualTo(0);
  }
}
