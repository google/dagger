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

import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class InjectStaticsTest {
  @Before public void setUp() {
    InjectsOneField.staticField = null;
    InjectsStaticAndNonStatic.staticField = null;
  }

  public static class InjectsOneField {
    @Inject static String staticField;
  }

  public static class InjectsStaticAndNonStatic {
    @Inject Integer nonStaticField;
    @Inject static String staticField;
  }

  @Test public void injectStatics() {
    @Module(staticInjections = InjectsOneField.class)
    class TestModule {
      @Provides String provideString() {
        return "static";
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertThat(InjectsOneField.staticField).isNull();
    graph.injectStatics();
    assertThat(InjectsOneField.staticField).isEqualTo("static");
  }

  @Test public void instanceFieldsNotInjectedByInjectStatics() {
    @Module(
        staticInjections = InjectsStaticAndNonStatic.class,
        entryPoints = InjectsStaticAndNonStatic.class)
    class TestModule {
      @Provides String provideString() {
        return "static";
      }
      @Provides Integer provideInteger() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertThat(InjectsStaticAndNonStatic.staticField).isNull();
    graph.injectStatics();
    assertThat(InjectsStaticAndNonStatic.staticField).isEqualTo("static");
  }

  @Test public void staticFieldsNotInjectedByInjectMembers() {
    @Module(
        staticInjections = InjectsStaticAndNonStatic.class,
        entryPoints = InjectsStaticAndNonStatic.class)
    class TestModule {
      @Provides String provideString() {
        throw new AssertionError();
      }
      @Provides Integer provideInteger() {
        return 5;
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertThat(InjectsStaticAndNonStatic.staticField).isNull();
    InjectsStaticAndNonStatic object = new InjectsStaticAndNonStatic();
    graph.inject(object);
    assertThat(InjectsStaticAndNonStatic.staticField).isNull();
    assertThat(object.nonStaticField).isEqualTo(5);
  }
}
