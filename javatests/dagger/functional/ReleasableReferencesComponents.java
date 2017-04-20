/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional;

import static dagger.functional.ReleasableReferencesComponents.Thing.thing;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoValue;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.releasablereferences.CanReleaseReferences;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.lang.annotation.Retention;
import java.util.Map;
import java.util.Set;
import javax.inject.Scope;

final class ReleasableReferencesComponents {

  interface ThingComponent {
    /**
     * A map whose keys are the scope annotations for each value. For unscoped values, the key is
     * the module that contains the unscoped binding. So for {@link Parent}, the unscoped {@link
     * Thing}'s key is {@link ParentModule ParentModule.class}; for {@link Child}, it is {@link
     * ChildModule ChildModule.class}.
     */
    Map<Class<?>, Thing> things();
  }

  @ParentRegularScope
  @ParentReleasableScope1
  @ParentReleasableScope2
  @Component(modules = ParentModule.class)
  interface Parent extends ThingComponent {

    Set<ReleasableReferenceManager> managers();

    Set<TypedReleasableReferenceManager<Metadata1>> typedReleasableReferenceManagers1();

    Set<TypedReleasableReferenceManager<Metadata2>> typedReleasableReferenceManagers2();

    @ForReleasableReferences(ParentReleasableScope1.class)
    ReleasableReferenceManager parentReleasableScope1Manager();

    @ForReleasableReferences(ParentReleasableScope2.class)
    ReleasableReferenceManager parentReleasableScope2Manager();

    @ForReleasableReferences(ParentReleasableScope2.class)
    TypedReleasableReferenceManager<Metadata1> parentReleasableScope2TypedReferenceManager();

    @ForReleasableReferences(ChildReleasableScope1.class)
    ReleasableReferenceManager childReleasableScope1Manager();

    @ForReleasableReferences(ChildReleasableScope2.class)
    ReleasableReferenceManager childReleasableScope2Manager();

    @ForReleasableReferences(ChildReleasableScope2.class)
    TypedReleasableReferenceManager<Metadata1> childReleasableScope2TypedReferenceManager1();

    @ForReleasableReferences(ChildReleasableScope2.class)
    TypedReleasableReferenceManager<Metadata2> childReleasableScope2TypedReferenceManager2();

    Child child();
  }

  @AutoValue
  abstract static class Thing {
    abstract int count();

    static Thing thing(int count) {
      return new AutoValue_ReleasableReferencesComponents_Thing(count);
    }
  }

  @ChildRegularScope
  @ChildReleasableScope1
  @ChildReleasableScope2
  @ChildReleasableScope3
  @Subcomponent(modules = ChildModule.class)
  interface Child extends ThingComponent {}

  @CanReleaseReferences
  @interface Metadata1 {
    String value();
  }

  @CanReleaseReferences
  @interface Metadata2 {
    String value();
  }

  @Retention(RUNTIME)
  @Scope
  @interface ParentRegularScope {}

  @Retention(RUNTIME)
  @Scope
  @interface ChildRegularScope {}

  @Retention(RUNTIME)
  @CanReleaseReferences
  @Scope
  @interface ParentReleasableScope1 {}

  @Retention(RUNTIME)
  @Metadata1("ParentReleasableScope2")
  @Scope
  @interface ParentReleasableScope2 {}

  @Retention(RUNTIME)
  @Metadata2("ChildReleasableScope1")
  @Scope
  @interface ChildReleasableScope1 {}

  @Retention(RUNTIME)
  @Metadata1("ChildReleasableScope2.1")
  @Metadata2("ChildReleasableScope2.2")
  @Scope
  @interface ChildReleasableScope2 {}

  @Retention(RUNTIME)
  @Metadata1("ChildReleasableScope3.1")
  @Metadata2("ChildReleasableScope3.2")
  @CanReleaseReferences
  @Scope
  @interface ChildReleasableScope3 {}

  @Module
  static final class ParentModule {
    private int unscopedCount;
    private int regularScopeCount;
    private int releasableScope1Count;
    private int releasableScope2Count;

    @Provides
    @IntoMap
    @ClassKey(ParentModule.class)
    Thing parentUnscopedThing() {
      return thing(++unscopedCount);
    }

    @Provides
    @IntoMap
    @ClassKey(ParentRegularScope.class)
    @ParentRegularScope
    Thing regularScopedThing() {
      return thing(++regularScopeCount);
    }

    @Provides
    @IntoMap
    @ClassKey(ParentReleasableScope1.class)
    @ParentReleasableScope1
    Thing releasableScope1Thing() {
      return thing(++releasableScope1Count);
    }

    @Provides
    @IntoMap
    @ClassKey(ParentReleasableScope2.class)
    @ParentReleasableScope2
    Thing releasableScope2Thing() {
      return thing(++releasableScope2Count);
    }
  }

  @Module
  static final class ChildModule {
    private int unscopedCount;
    private int regularScopeCount;
    private int releasableScope1Count;
    private int releasableScope2Count;

    @Provides
    @IntoMap
    @ClassKey(ChildModule.class)
    Thing childUnscopedThing() {
      return thing(++unscopedCount);
    }

    @Provides
    @IntoMap
    @ClassKey(ChildRegularScope.class)
    @ChildRegularScope
    Thing regularScopedThing() {
      return thing(++regularScopeCount);
    }

    @Provides
    @IntoMap
    @ClassKey(ChildReleasableScope1.class)
    @ChildReleasableScope1
    Thing releasableScope1Thing() {
      return thing(++releasableScope1Count);
    }

    @Provides
    @IntoMap
    @ClassKey(ChildReleasableScope2.class)
    @ChildReleasableScope2
    Thing releasableScope2Thing() {
      return thing(++releasableScope2Count);
    }
  }
}
