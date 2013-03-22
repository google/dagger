/*
 * Copyright (C) 2010 Google Inc.
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

import dagger.internal.Binding;
import dagger.internal.ModuleAdapter;
import dagger.internal.Plugin;
import dagger.internal.StaticInjection;
import dagger.internal.plugins.loading.ClassloadingPlugin;
import dagger.internal.plugins.reflect.ReflectivePlugin;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class CreationTest {

  static class EntryPoint {
    @Inject EntryPoint() { }
  }

  @Module(entryPoints = EntryPoint.class)
  static class TestModule { }

  @Test public void noReflectionCreation_FailOnNoPlugins() {
    try {
      ObjectGraph.with();
      fail("Should throw.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("Must provide at least one plugin.");
    }
  }

  @Test public void noReflectionCreation_FailOnModuleLoad() {
    try {
      ObjectGraph.with(new ClassloadingPlugin()).create(TestModule.class);
      fail("Should throw.");
    } catch (RuntimeException e) {
      assertThat(e.getCause()).isExactlyInstanceOf(ClassNotFoundException.class);
      assertThat(e.getMessage()).contains("Adapter loader could not find ");
      assertThat(e.getMessage()).contains("$ModuleAdapter");
    }
  }

  @Test public void noReflectionCreation_FailOnAtInject() {
    final Plugin reflectivePlugin = new ReflectivePlugin();
    Plugin testingPlugin = new Plugin() {
      @Override
      public Binding<?> getAtInjectBinding(String key, String className, boolean mustBeInjectable) {
        throw new RuntimeException("Fake error");
      }
      @Override
      public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
        return reflectivePlugin.getModuleAdapter(moduleClass, module);
      }
      @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
        throw new RuntimeException("Fake error");
      }
    };
    ObjectGraph graph = ObjectGraph.with(testingPlugin, new ClassloadingPlugin())
        .create(TestModule.class);
    try {
      graph.get(EntryPoint.class);
      fail("Should throw.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Adapter loader could not find ");
      assertThat(e.getMessage()).contains("$InjectAdapter");
      assertThat(e.getMessage()).contains("required by class dagger.CreationTest$TestModule");
    }
  }
}
