/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.subcomponent;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Regression tests for an issue where subcomponent builder bindings were incorrectly reused from
 * a parent even if the subcomponent were redeclared on the child component. This manifested via
 * multibindings, especially since subcomponent builder bindings are special in that we cannot
 * traverse them to see if they depend on local multibinding contributions.
 */
@RunWith(JUnit4.class)
public final class SubcomponentBuilderMultibindingsTest {

  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  public @interface ParentFoo{}

  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  public @interface ChildFoo{}

  public static final class Foo {
    final Set<String> multi;
    @Inject Foo(Set<String> multi) {
      this.multi = multi;
    }
  }

  // This tests the case where a subcomponent is installed in both the parent and child component.
  // In this case, we expect two subcomponents to be generated with the child one including the
  // child multibinding contribution.
  public static final class ChildInstallsFloating {
    @Component(modules = ParentModule.class)
    public interface Parent {
      @ParentFoo Foo getParentFoo();

      Child getChild();
    }

    @Subcomponent(modules = ChildModule.class)
    public interface Child {
      @ChildFoo Foo getChildFoo();
    }

    @Subcomponent
    public interface FloatingSub {
      Foo getFoo();

      @Subcomponent.Builder
      public interface Builder {
        FloatingSub build();
      }
    }

    @Module(subcomponents = FloatingSub.class)
    public interface ParentModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "parent";
      }

      @Provides
      @ParentFoo
      static Foo provideParentFoo(FloatingSub.Builder builder) {
        return builder.build().getFoo();
      }
    }

    // The subcomponent installation of FloatingSub here is the key difference
    @Module(subcomponents = FloatingSub.class)
    public interface ChildModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "child";
      }

      @Provides
      @ChildFoo
      static Foo provideChildFoo(FloatingSub.Builder builder) {
        return builder.build().getFoo();
      }
    }

    private ChildInstallsFloating() {}
  }

  // This is the same as the above, except this time the child does not install the subcomponent
  // builder. Here, we expect the child to reuse the parent subcomponent binding (we want to avoid
  // any mistakes that might implicitly create a new subcomponent relationship) and so therefore
  // we expect only one subcomponent to be generated in the parent resulting in the child not seeing
  // the child multibinding contribution.
  public static final class ChildDoesNotInstallFloating {
    @Component(modules = ParentModule.class)
    public interface Parent {
      @ParentFoo Foo getParentFoo();

      Child getChild();
    }

    @Subcomponent(modules = ChildModule.class)
    public interface Child {
      @ChildFoo Foo getChildFoo();
    }

    @Subcomponent
    public interface FloatingSub {
      Foo getFoo();

      @Subcomponent.Builder
      public interface Builder {
        FloatingSub build();
      }
    }

    @Module(subcomponents = FloatingSub.class)
    public interface ParentModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "parent";
      }

      @Provides
      @ParentFoo
      static Foo provideParentFoo(FloatingSub.Builder builder) {
        return builder.build().getFoo();
      }
    }

    // The lack of a subcomponent installation of FloatingSub here is the key difference
    @Module
    public interface ChildModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "child";
      }

      @Provides
      @ChildFoo
      static Foo provideChildFoo(FloatingSub.Builder builder) {
        return builder.build().getFoo();
      }
    }

    private ChildDoesNotInstallFloating() {}
  }

  // This is similar to the first, except this time the components installs the subcomponent via
  // factory methods. Here, we expect the child to get a new subcomponent and so should see its
  // multibinding contribution.
  public static final class ChildInstallsFloatingFactoryMethod {
    @Component(modules = ParentModule.class)
    public interface Parent {
      @ParentFoo Foo getParentFoo();

      Child getChild();

      FloatingSub getFloatingSub();
    }

    @Subcomponent(modules = ChildModule.class)
    public interface Child {
      @ChildFoo Foo getChildFoo();

      FloatingSub getFloatingSub();
    }

    @Subcomponent
    public interface FloatingSub {
      Foo getFoo();
    }

    @Module
    public interface ParentModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "parent";
      }

      @Provides
      @ParentFoo
      static Foo provideParentFoo(Parent componentSelf) {
        return componentSelf.getFloatingSub().getFoo();
      }
    }

    @Module
    public interface ChildModule {
      @Provides
      @IntoSet
      static String provideStringMulti() {
         return "child";
      }

      @Provides
      @ChildFoo
      static Foo provideChildFoo(Child componentSelf) {
        return componentSelf.getFloatingSub().getFoo();
      }
    }

    private ChildInstallsFloatingFactoryMethod() {}
  }

  @Test
  public void testChildInstallsFloating() {
    ChildInstallsFloating.Parent parentComponent =
        DaggerSubcomponentBuilderMultibindingsTest_ChildInstallsFloating_Parent.create();
    assertThat(parentComponent.getParentFoo().multi).containsExactly("parent");
    assertThat(parentComponent.getChild().getChildFoo().multi).containsExactly("parent", "child");
  }

  @Test
  public void testChildDoesNotInstallFloating() {
    ChildDoesNotInstallFloating.Parent parentComponent =
        DaggerSubcomponentBuilderMultibindingsTest_ChildDoesNotInstallFloating_Parent.create();
    assertThat(parentComponent.getParentFoo().multi).containsExactly("parent");
    // Don't expect the child contribution because the child didn't redeclare the subcomponent
    // dependency, meaning it intends to just use the subcomponent relationship from the parent
    // component.
    assertThat(parentComponent.getChild().getChildFoo().multi).containsExactly("parent");
  }

  @Test
  public void testChildInstallsFloatingFactoryMethod() {
    ChildInstallsFloatingFactoryMethod.Parent parentComponent =
        DaggerSubcomponentBuilderMultibindingsTest_ChildInstallsFloatingFactoryMethod_Parent.create();
    assertThat(parentComponent.getParentFoo().multi).containsExactly("parent");
    assertThat(parentComponent.getChild().getChildFoo().multi).containsExactly("parent", "child");
  }
}
