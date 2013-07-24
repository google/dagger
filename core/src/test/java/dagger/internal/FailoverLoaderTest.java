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
package dagger.internal;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

/**
 * A test case to deal with fall-back to reflection where the concrete type has been generated
 * but the parent has no {@code @Inject} annotation, and so has not been generated.
 */
@RunWith(JUnit4.class)
public final class FailoverLoaderTest {

  @Module(injects = EntryPoint.class)
  static class TestModule {
    @Provides String aString() { return "a"; }
  }

  /** A reflective module that will be loaded in place of a generated module for this test. */
  static final class TestModule$$ModuleAdapter extends TestingModuleAdapter<TestModule> {
    public TestModule$$ModuleAdapter() {
      super(TestModule.class, TestModule.class.getAnnotation(Module.class));
    }
  }

  static class EntryPoint {
    @Inject String a;
  }

  @Test public void simpleInjectionWithUnGeneratedCode() {
    EntryPoint entryPoint = new EntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.a).isEqualTo("a");
  }
}
