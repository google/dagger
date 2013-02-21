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
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests MembersInjector injection, and how object graph features interact with
 * types unconstructable types (types that support members injection only).
 */
@RunWith(JUnit4.class)
public final class MembersInjectorTest {
  @Test public void injectMembers() {
    class TestEntryPoint {
      @Inject MembersInjector<Injectable> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class StringModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new StringModule()).inject(entryPoint);
    Injectable injectable = new Injectable();
    entryPoint.membersInjector.injectMembers(injectable);
    assertThat(injectable.injected).isEqualTo("injected");
  }

  static class Injectable {
    @Inject String injected;
  }

  static class Unconstructable {
    final String constructor;
    @Inject String injected;
    Unconstructable(String constructor) {
      this.constructor = constructor;
    }
  }

  @Test public void membersInjectorOfUnconstructableIsOkay() {
    class TestEntryPoint {
      @Inject MembersInjector<Unconstructable> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class StringModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new StringModule()).inject(entryPoint);
    Unconstructable object = new Unconstructable("constructor");
    entryPoint.membersInjector.injectMembers(object);
    assertThat(object.constructor).isEqualTo("constructor");
    assertThat(object.injected).isEqualTo("injected");
  }


  @Test public void injectionOfUnconstructableFails() {
    class TestEntryPoint {
      @Inject Unconstructable unconstructable;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(TestEntryPoint.class);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void instanceInjectionOfMembersOnlyType() {
    class TestEntryPoint {
      @Inject Provider<Unconstructable> provider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(TestEntryPoint.class);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void rejectUnconstructableSingleton() {
    class TestEntryPoint {
      @Inject MembersInjector<UnconstructableSingleton> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(TestEntryPoint.class);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Singleton
  static class UnconstructableSingleton {
    final String constructor;
    @Inject String injected;
    UnconstructableSingleton(String constructor) {
      this.constructor = constructor;
    }
  }

  class NonStaticInner {
    @Inject String injected;
  }

  @Test public void membersInjectorOfNonStaticInnerIsOkay() {
    class TestEntryPoint {
      @Inject MembersInjector<NonStaticInner> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    NonStaticInner nonStaticInner = new NonStaticInner();
    entryPoint.membersInjector.injectMembers(nonStaticInner);
    assertThat(nonStaticInner.injected).isEqualTo("injected");
  }

  @Test public void instanceInjectionOfNonStaticInnerFailsEarly() {
    class TestEntryPoint {
      @Inject NonStaticInner nonStaticInner;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(TestEntryPoint.class);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void providesMethodsAndMembersInjectionDoNotConflict() {
    class InjectsString {
      @Inject String value;
    }

    class TestEntryPoint {
      @Inject Provider<InjectsString> provider;
      @Inject MembersInjector<InjectsString> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides InjectsString provideInjectsString() {
        InjectsString result = new InjectsString();
        result.value = "provides";
        return result;
      }
      @Provides String provideString() {
        return "members";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);

    InjectsString provided = entryPoint.provider.get();
    assertThat(provided.value).isEqualTo("provides");

    InjectsString membersInjected = new InjectsString();
    entryPoint.membersInjector.injectMembers(membersInjected);
    assertThat(membersInjected.value).isEqualTo("members");
  }
}