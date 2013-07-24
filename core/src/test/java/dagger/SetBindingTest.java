/*
 * Copyright (C) 2012 Google Inc.
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

import dagger.internal.TestingLoader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static java.util.Collections.emptySet;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(JUnit4.class)
public final class SetBindingTest {
  @Test public void multiValueBindings_SingleModule() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET) String provideFirstString() { return "string1"; }
      @Provides(type=SET) String provideSecondString() { return "string2"; }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(set("string1", "string2"), ep.strings);
  }

  @Test public void multiValueBindings_MultiModule() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module
    class TestIncludesModule {
      @Provides(type=SET) String provideSecondString() { return "string2"; }
    }

    @Module(injects = TestEntryPoint.class, includes = TestIncludesModule.class)
    class TestModule {
      @Provides(type=SET) String provideFirstString() { return "string1"; }

      @Provides(type=SET_VALUES) Set<String> provideDefaultStrings() {
        return emptySet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(),
        new TestModule(), new TestIncludesModule());
    assertEquals(set("string1", "string2"), ep.strings);
  }

  @Test public void multiValueBindings_MultiModule_NestedSet() {
    class TestEntryPoint {
      @Inject Set<Set<String>> stringses;
    }

    @Module
    class TestIncludesModule {
      @Provides(type=SET) Set<String> provideSecondStrings() { return set("string2"); }
    }

    @Module(injects = TestEntryPoint.class, includes = TestIncludesModule.class)
    class TestModule {
      @Provides(type=SET) Set<String> provideFirstStrings() { return set("string1"); }

      @Provides(type=SET_VALUES) Set<Set<String>> provideDefaultStringeses() {
        return set(set("string3"));
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(),
        new TestModule(), new TestIncludesModule());
    assertEquals(set(set("string1"),set("string2"), set("string3")), ep.stringses);
  }

  @Test public void multiValueBindings_WithSingletonAndDefaultValues() {
    final AtomicInteger singletonCounter = new AtomicInteger(100);
    final AtomicInteger defaultCounter = new AtomicInteger(200);
    class TestEntryPoint {
      @Inject Set<Integer> objects1;
      @Inject Set<Integer> objects2;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET) @Singleton Integer a() { return singletonCounter.getAndIncrement(); }
      @Provides(type=SET) Integer b() { return defaultCounter.getAndIncrement(); }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(set(100, 200), ep.objects1);
    assertEquals(set(100, 201), ep.objects2);
  }

  @Test public void multiValueBindings_WithSingletonsAcrossMultipleInjectableTypes() {
    final AtomicInteger singletonCounter = new AtomicInteger(100);
    final AtomicInteger defaultCounter = new AtomicInteger(200);
    class TestEntryPoint1 {
      @Inject Set<Integer> objects1;
    }
    class TestEntryPoint2 {
      @Inject Set<Integer> objects2;
    }

    @Module(injects = { TestEntryPoint1.class, TestEntryPoint2.class })
    class TestModule {
      @Provides(type=SET) @Singleton Integer a() { return singletonCounter.getAndIncrement(); }
      @Provides(type=SET) Integer b() { return defaultCounter.getAndIncrement(); }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    TestEntryPoint1 ep1 = graph.inject(new TestEntryPoint1());
    TestEntryPoint2 ep2 = graph.inject(new TestEntryPoint2());
    assertEquals(set(100, 200), ep1.objects1);
    assertEquals(set(100, 201), ep2.objects2);

 }

  @Test public void multiValueBindings_WithQualifiers() {
    class TestEntryPoint {
      @Inject Set<String> strings;
      @Inject @Named("foo") Set<String> fooStrings;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET_VALUES) Set<String> provideString1() {
        return set("string1");
      }
      @Provides(type=SET) String provideString2() { return "string2"; }
      @Provides(type=SET) @Named("foo") String provideString3() { return "string3"; }
      @Provides(type=SET_VALUES) @Named("foo") Set<String> provideString4() {
        return set("string4");
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(set("string1", "string2"), ep.strings);
    assertEquals(set("string4", "string3"), ep.fooStrings);
  }

  // TODO(cgruber): Move this into an example project.
  @Test public void sampleMultiBindingLogger() {
    class TestEntryPoint {
      @Inject Logger logger;
      public void doStuff() {
        Throwable t = new NullPointerException("Naughty Naughty");
        this.logger.log("Logging an error", t);
      }
    }

    final AtomicReference<String> logoutput = new AtomicReference<String>();
    @Module
    class LogModule {
      @Provides(type=SET) LogSink outputtingLogSink() {
        return new LogSink() {
          @Override public void log(LogMessage message) {
            StringWriter sw = new StringWriter();
            message.error.printStackTrace(new PrintWriter(sw));
            logoutput.set(message.message + "\n" + sw.getBuffer().toString());
          }
        };
      }
    }
    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET) LogSink nullLogger() {
        return new LogSink() { @Override public void log(LogMessage message) {} };
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(),new TestModule(), new LogModule());
    assertNull(logoutput.get());
    ep.doStuff();
    assertNotNull(logoutput.get());
    assertThat(logoutput.get()).contains("Naughty Naughty");
    assertThat(logoutput.get()).contains("NullPointerException");
  }

  @Test public void validateSetBinding() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET) String provideString1() { return "string1"; }
      @Provides(type=SET) String provideString2() { return "string2"; }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    graph.validate();
  }

  @Test public void validateEmptySetBinding() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides(type=SET_VALUES) Set<String> provideDefault() {
        return emptySet();
      }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    graph.validate();
  }

  static class Logger {
    @Inject Set<LogSink> loggers;
    public void log(String text, Throwable error) {
      LogMessage m = new LogMessage(text, error);
      for (LogSink sink : loggers) {
        sink.log(m);
      }
    }
  }

  static class LogMessage {
    public final String message;
    public final Throwable error;
    public LogMessage (String message, Throwable error) {
      this.message = message;
      this.error = error;
    }
  }

  static interface LogSink {
    void log(LogMessage message);
  }

  private <T> T injectWithModule(T ep, Object ... modules) {
    return ObjectGraph.createWith(new TestingLoader(), modules).inject(ep);
  }

  private <T> Set<T> set(T... ts) {
    return new LinkedHashSet<T>(Arrays.asList(ts));
  }

}
