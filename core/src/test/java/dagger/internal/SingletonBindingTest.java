/**
 * Copyright (C) 2013 Square, Inc.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.assertj.core.api.Assertions.*;

@RunWith(JUnit4.class)
public final class SingletonBindingTest {
  private Binding<String> wrappedBinding;
  private Binding<String> singletonBinding;

  @Before public void setUp() {
    wrappedBinding = new StringBinding();
    singletonBinding = Linker.scope(wrappedBinding);
  }

  @Test public void testSingletonBindingIsSingleton() {
    assertThat(singletonBinding.isSingleton()).isTrue();
  }

  // This next batch of tests validates that SingletonBinding consistently delegates to the wrapped binding for state.
  @Test public void testSingletonBindingDelegatesSetLinked() {
    singletonBinding.setLinked();
    assertThat(wrappedBinding.isLinked()).isTrue();
  }

  @Test public void testSingletonBindingDelegatesIsLinked() {
    wrappedBinding.setLinked();
    assertThat(singletonBinding.isLinked()).isTrue();
  }

  @Test public void testSingletonBindingDelegatesSetVisiting() {
    singletonBinding.setVisiting(true);
    assertThat(wrappedBinding.isVisiting()).isTrue();
  }

  @Test public void testSingletonBindingDelegatesIsVisiting() {
    wrappedBinding.setVisiting(true);
    assertThat(singletonBinding.isVisiting()).isTrue();
  }

  @Test public void testSingletonBindingDelegatesSetCycleFree() {
    singletonBinding.setCycleFree(true);
    assertThat(wrappedBinding.isCycleFree()).isTrue();
  }

  @Test public void testSingletonBindingDelegatesIsCycleFree() {
    wrappedBinding.setCycleFree(true);
    assertThat(singletonBinding.isCycleFree()).isTrue();
  }

  private static class StringBinding extends Binding<String> {
    private StringBinding() {
      super("dummy", "dummy", true, "dummy"); // 3rd arg true => singleton
    }

  }
}
