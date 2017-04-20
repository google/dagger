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

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

final class MultibindingSubcomponents {

  /** Multibindings for this type are bound only in the parent component. */
  enum BoundInParent {
    INSTANCE;
  }

  /** Multibindings for this type are bound only in the child component. */
  enum BoundInChild {
    INSTANCE;
  }

  /** Multibindings for this type are bound in the parent component and the child component. */
  enum BoundInParentAndChild {
    IN_PARENT,
    IN_CHILD;
  }

  static final class RequiresMultibindings<T> {
    private final Set<T> set;
    private final Map<String, T> map;

    @Inject
    RequiresMultibindings(Set<T> set, Map<String, T> map) {
      this.set = set;
      this.map = map;
    }

    Set<T> set() {
      return set;
    }

    Map<String, T> map() {
      return map;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RequiresMultibindings<?>
          && set.equals(((RequiresMultibindings<?>) obj).set)
          && map.equals(((RequiresMultibindings<?>) obj).map);
    }

    @Override
    public int hashCode() {
      return Objects.hash(set, map);
    }

    @Override
    public String toString() {
      return String.format(
          "%s{set=%s, map=%s}", RequiresMultibindings.class.getSimpleName(), set, map);
    }
  }

  @Module
  abstract static class ParentMultibindingModule {

    @Provides
    @IntoSet
    static BoundInParent onlyInParentElement() {
      return BoundInParent.INSTANCE;
    }

    @Provides
    @IntoMap
    @StringKey("parent key")
    static BoundInParent onlyInParentEntry() {
      return BoundInParent.INSTANCE;
    }

    @Provides
    @IntoSet
    static BoundInParentAndChild inParentAndChildElement() {
      return BoundInParentAndChild.IN_PARENT;
    }

    @Provides
    @IntoMap
    @StringKey("parent key")
    static BoundInParentAndChild inParentAndChildEntry() {
      return BoundInParentAndChild.IN_PARENT;
    }

    /* This is not static because otherwise we have no tests that cover the case where a
     * subcomponent uses a module instance installed onto a parent component. */
    @Binds
    @IntoSet
    abstract RequiresMultibindings<BoundInParentAndChild>
        requiresMultibindingsInParentAndChildElement(
            RequiresMultibindings<BoundInParentAndChild> requiresMultibindingsInParentAndChild);
  }

  @Module
  static final class ChildMultibindingModule {

    @Provides
    @IntoSet
    static BoundInParentAndChild inParentAndChildElement() {
      return BoundInParentAndChild.IN_CHILD;
    }

    @Provides
    @IntoMap
    @StringKey("child key")
    static BoundInParentAndChild inParentAndChildEntry() {
      return BoundInParentAndChild.IN_CHILD;
    }

    @Provides
    @IntoSet
    static BoundInChild onlyInChildElement() {
      return BoundInChild.INSTANCE;
    }

    @Provides
    @IntoMap
    @StringKey("child key")
    static BoundInChild onlyInChildEntry() {
      return BoundInChild.INSTANCE;
    }
  }

  @Module
  abstract static class ChildMultibindingModuleWithOnlyBindsMultibindings {
    @Provides
    static BoundInParentAndChild provideBoundInParentAndChildForBinds() {
      return BoundInParentAndChild.IN_CHILD;
    }

    @Binds
    @IntoSet
    abstract BoundInParentAndChild bindsLocalContribution(BoundInParentAndChild instance);

    @Binds
    @IntoMap
    @StringKey("child key")
    abstract BoundInParentAndChild inParentAndChildEntry(BoundInParentAndChild instance);

    @Provides
    static BoundInChild provideBoundInChildForBinds() {
      return BoundInChild.INSTANCE;
    }

    @Binds
    @IntoSet
    abstract BoundInChild inChild(BoundInChild instance);

    @Binds
    @IntoMap
    @StringKey("child key")
    abstract BoundInChild inChildEntry(BoundInChild instance);
  }

  interface ProvidesBoundInParent {
    RequiresMultibindings<BoundInParent> requiresMultibindingsBoundInParent();
  }

  interface ProvidesBoundInChild {
    RequiresMultibindings<BoundInChild> requiresMultibindingsBoundInChild();
  }

  interface ProvidesBoundInParentAndChild {
    RequiresMultibindings<BoundInParentAndChild> requiresMultibindingsBoundInParentAndChild();
  }

  interface ProvidesSetOfRequiresMultibindings {
    Set<RequiresMultibindings<BoundInParentAndChild>> setOfRequiresMultibindingsInParentAndChild();
  }

  interface ParentWithProvision
      extends ProvidesBoundInParent, ProvidesBoundInParentAndChild,
          ProvidesSetOfRequiresMultibindings {}

  interface HasChildWithProvision {
    ChildWithProvision childWithProvision();
  }

  interface HasChildWithoutProvision {
    ChildWithoutProvision childWithoutProvision();
  }

  @Component(modules = ParentMultibindingModule.class)
  interface ParentWithoutProvisionHasChildWithoutProvision extends HasChildWithoutProvision {}

  @Component(modules = ParentMultibindingModule.class)
  interface ParentWithoutProvisionHasChildWithProvision extends HasChildWithProvision {}

  @Component(modules = ParentMultibindingModule.class)
  interface ParentWithProvisionHasChildWithoutProvision
      extends ParentWithProvision, HasChildWithoutProvision {}

  @Component(modules = ParentMultibindingModule.class)
  interface ParentWithProvisionHasChildWithProvision
      extends ParentWithProvision, HasChildWithProvision {}

  @Subcomponent(modules = ChildMultibindingModule.class)
  interface ChildWithoutProvision {
    Grandchild grandchild();
  }

  @Subcomponent(modules = ChildMultibindingModule.class)
  interface ChildWithProvision
      extends ProvidesBoundInParent, ProvidesBoundInParentAndChild, ProvidesBoundInChild,
          ProvidesSetOfRequiresMultibindings {

    Grandchild grandchild();
  }

  @Subcomponent
  interface Grandchild
      extends ProvidesBoundInParent, ProvidesBoundInParentAndChild, ProvidesBoundInChild,
          ProvidesSetOfRequiresMultibindings {}

  @Component(modules = ParentMultibindingModule.class)
  interface ParentWithProvisionHasChildWithBinds extends ParentWithProvision {
    ChildWithBinds childWithBinds();
  }

  @Subcomponent(modules = ChildMultibindingModuleWithOnlyBindsMultibindings.class)
  interface ChildWithBinds extends ChildWithProvision {}

}
