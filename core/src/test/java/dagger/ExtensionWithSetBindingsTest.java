/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2013 Square Inc.
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
package dagger;

import dagger.internal.TestingLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dagger.Provides.Type.SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(JUnit4.class)
public final class ExtensionWithSetBindingsTest {
  private static final AtomicInteger counter = new AtomicInteger(0);

  @Singleton
  static class RealSingleton {
    @Inject Set<Integer> ints;
  }

  @Singleton
  static class Main {
    @Inject Set<Integer> ints;
  }

  @Module(injects = RealSingleton.class)
  static class RootModule {
    @Provides(type=SET) @Singleton Integer provideA() { return counter.getAndIncrement(); }
    @Provides(type=SET) @Singleton Integer provideB() { return counter.getAndIncrement(); }
  }

  @Module(addsTo = RootModule.class, injects = Main.class )
  static class ExtensionModule {
    @Provides(type=SET) @Singleton Integer provideC() { return counter.getAndIncrement(); }
    @Provides(type=SET) @Singleton Integer provideD() { return counter.getAndIncrement(); }
  }

  @Module
  static class EmptyModule {
  }

  @Module(library = true)
  static class DuplicateModule {
    @Provides @Singleton String provideFoo() { return "foo"; }
    @Provides @Singleton String provideBar() { return "bar"; }
  }

  @Test public void basicInjectionWithExtension() {
    ObjectGraph root = ObjectGraph.createWith(new TestingLoader(), new RootModule());
    RealSingleton rs = root.get(RealSingleton.class);
    assertThat(rs.ints).containsOnly(0, 1);

    ObjectGraph extension = root.plus(new ExtensionModule());
    Main main = extension.get(Main.class);
    assertThat(main.ints).containsOnly(0, 1, 2, 3);

    // Second time around.
    ObjectGraph extension2 = root.plus(new ExtensionModule());
    Main main2 = extension2.get(Main.class);
    assertThat(main2.ints).containsOnly(0, 1, 4, 5);
  }

  @Module(includes = ExtensionModule.class, overrides = true)
  static class TestModule {
    @Provides(type=SET) @Singleton Integer provide9999() { return 9999; }
  }

  @Test public void basicInjectionWithExtensionAndOverrides() {
    try {
      ObjectGraph.createWith(new TestingLoader(), new RootModule()).plus(new TestModule());
      fail("Should throw exception.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo("TestModule: Module overrides cannot contribute set bindings.");
    }
  }

  @Test public void duplicateBindingsInSecondaryModule() {
    try {
      ObjectGraph.createWith(new TestingLoader(), new EmptyModule(), new DuplicateModule());
      fail("Should throw exception.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage().startsWith("DuplicateModule: Duplicate")).isTrue();
    }
  }
}
