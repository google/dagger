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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;

@RunWith(JUnit4.class)
public final class InstanceFactoryTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test public void instanceFactory() {
    Object instance = new Object();
    Factory<Object> factory = InstanceFactory.create(instance);
    assert_().that(factory.get()).isEqualTo(instance);
    assert_().that(factory.get()).isEqualTo(instance);
    assert_().that(factory.get()).isEqualTo(instance);
  }

  @Test public void create_throwsNullPointerException() {
    thrown.expect(NullPointerException.class);
    InstanceFactory.create(null);
  }
}
