/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.testing;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class TestRootModulesTest {
  @Rule public final HiltAndroidRule rules = new HiltAndroidRule(this);

  @Retention(RUNTIME)
  @Qualifier
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  public @interface TestQualifier {
    int value();
  }

  @Inject
  @TestQualifier(0)
  String testString0;

  @Inject
  @TestQualifier(1)
  String testString1;

  @Inject
  @TestQualifier(2)
  String testString2;

  @Inject
  @TestQualifier(3)
  String testString3;

  @Inject
  @TestQualifier(4)
  String testString4;

  @Inject FooImpl fooImpl;
  @Inject Foo foo;

  @Module
  @InstallIn(SingletonComponent.class)
  public final class NonStaticModuleNonStaticProvidesDefaultConstructor {
    @Provides
    @TestQualifier(0)
    String provideString() {
      return "0";
    }

    NonStaticModuleNonStaticProvidesDefaultConstructor() {}
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public final class NonStaticModuleNonStaticProvides {
    @Provides
    @TestQualifier(1)
    String provideString() {
      return "1";
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public static final class StaticModuleStaticProvides {
    @Provides
    @TestQualifier(2)
    static String provideString() {
      return "2";
    }

    private StaticModuleStaticProvides() {}
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public static final class StaticModuleNonStaticProvidesDefaultConstructor {
    @Provides
    @TestQualifier(3)
    String provideString() {
      return "3";
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public abstract static class AbstractModuleStaticProvides {
    @Provides
    @TestQualifier(4)
    static String provideString() {
      return "4";
    }

    private AbstractModuleStaticProvides() {}
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public abstract static class AbstractModuleBindsMethod {
    @Binds
    abstract Foo foo(FooImpl fooImpl);
  }

  interface Foo {}

  @Singleton
  static final class FooImpl implements Foo {
    @Inject
    FooImpl() {}
  }

  @Test
  public void testInjection() throws Exception {
    rules.inject();
    assertEquals("0", testString0);
    assertEquals("1", testString1);
    assertEquals("2", testString2);
    assertEquals("3", testString3);
    assertEquals("4", testString4);
    assertEquals(fooImpl, foo);
  }
}
