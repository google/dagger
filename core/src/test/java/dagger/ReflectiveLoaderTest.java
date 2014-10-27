/*
 * Copyright (C) 2012 Square Inc.
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

import dagger.internal.Loader;
import dagger.internal.ReflectiveLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Provider;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.fail;

/**
 * @deprecated This exists to test ReflectiveLoader which is obsolete but needed to work around
 *     proguard obfuscation.
 */
@Deprecated
@RunWith(JUnit4.class)
public final class ReflectiveLoaderTest {
  private static final Loader LOADER = new ReflectiveLoader();

  static class TestEntryPoint {
    @Inject String s;
  }

  @Module(injects = TestEntryPoint.class)
  static class ModuleWithEntryPoint {
  }

  @Test public void childModuleWithEntryPoint() {
    @Module(includes = ModuleWithEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new TestModule());
    TestEntryPoint entryPoint = objectGraph.get(TestEntryPoint.class);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  static class TestStaticInjection {
    @Inject static String s;
  }

  @Module(staticInjections = TestStaticInjection.class)
  static class ModuleWithStaticInjection {
  }

  @Test public void childModuleWithStaticInjection() {
    @Module(includes = ModuleWithStaticInjection.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new TestModule());
    TestStaticInjection.s = null;
    objectGraph.injectStatics();
    assertThat(TestStaticInjection.s).isEqualTo("injected");
  }

  @Module
  static class ModuleWithBinding {
    @Provides String provideString() {
      return "injected";
    }
  }

  @Test public void childModuleWithBinding() {

    @Module(
        injects = TestEntryPoint.class,
        includes = ModuleWithBinding.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  @Module(includes = ModuleWithBinding.class)
  static class ModuleWithChildModule {
  }

  @Test public void childModuleWithChildModule() {

    @Module(
        injects = TestEntryPoint.class,
        includes = ModuleWithChildModule.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  @Module
  static class ModuleWithConstructor {
    private final String value;

    ModuleWithConstructor(String value) {
      this.value = value;
    }

    @Provides String provideString() {
      return value;
    }
  }

  @Test public void childModuleMissingManualConstruction() {
    @Module(includes = ModuleWithConstructor.class)
    class TestModule {
    }

    try {
      ObjectGraph.createWith(LOADER, new TestModule());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void childModuleWithManualConstruction() {

    @Module(
        injects = TestEntryPoint.class,
        includes = ModuleWithConstructor.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new ModuleWithConstructor("a"), new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("a");
  }

  static class A {}

  static class B { @Inject A a; }

  @Module(injects = A.class) public static class TestModuleA {
    @Provides A a() { return new A(); }
  }

  @Module(includes = TestModuleA.class, injects = B.class) public static class TestModuleB {}

  @Test public void autoInstantiationOfModules() {
    // Have to make these non-method-scoped or instantiation errors occur.
    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, TestModuleA.class);
    assertThat(objectGraph.get(A.class)).isNotNull();
  }

  @Test public void autoInstantiationOfIncludedModules() {
    // Have to make these non-method-scoped or instantiation errors occur.
    ObjectGraph objectGraph = ObjectGraph.createWith(LOADER, new TestModuleB()); // TestModuleA auto-created.
    assertThat(objectGraph.get(A.class)).isNotNull();
    assertThat(objectGraph.get(B.class).a).isNotNull();
  }

  static class ModuleMissingModuleAnnotation {}

  @Module(includes = ModuleMissingModuleAnnotation.class)
  static class ChildModuleMissingModuleAnnotation {}

  @Test
  public void childModuleMissingModuleAnnotation() {
    try {
      ObjectGraph.createWith(LOADER, new ChildModuleMissingModuleAnnotation());
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage())
          .contains("No @Module on ").contains("ModuleMissingModuleAnnotation");
    }
  }

  @Module
  static class ThreadModule extends Thread {}

  @Test public void moduleExtendingClassThrowsException() {
    try {
      ObjectGraph.createWith(LOADER, new ThreadModule());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Modules must not extend from other classes: ");
    }
  }

  @Test public void provideProviderFails() {
    @Module
    class ProvidesProviderModule {
      @Provides Provider<Object> provideObject() {
        return null;
      }
    }
    try {
      ObjectGraph.createWith(LOADER, new ProvidesProviderModule());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()) //
          .startsWith("@Provides method must not return Provider directly: ")
          .endsWith("ProvidesProviderModule.provideObject");
    }
  }

  @Test public void provideRawProviderFails() {
    @Module
    class ProvidesRawProviderModule {
      @Provides Provider provideObject() {
        return null;
      }
    }
    try {
      ObjectGraph.createWith(LOADER, new ProvidesRawProviderModule());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()) //
          .startsWith("@Provides method must not return Provider directly: ")
          .endsWith("ProvidesRawProviderModule.provideObject");
    }
  }

  @Test public void provideLazyFails() {
    @Module
    class ProvidesLazyModule {
      @Provides Lazy<Object> provideObject() {
        return null;
      }
    }
    try {
      ObjectGraph.createWith(LOADER, new ProvidesLazyModule());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()) //
          .startsWith("@Provides method must not return Lazy directly: ")
          .endsWith("ProvidesLazyModule.provideObject");
    }
  }

  @Test public void provideRawLazyFails() {
    @Module
    class ProvidesRawLazyModule {
      @Provides Lazy provideObject() {
        return null;
      }
    }
    try {
      ObjectGraph.createWith(LOADER, new ProvidesRawLazyModule());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()) //
          .startsWith("@Provides method must not return Lazy directly: ")
          .endsWith("ProvidesRawLazyModule.provideObject");
    }
  }
}
