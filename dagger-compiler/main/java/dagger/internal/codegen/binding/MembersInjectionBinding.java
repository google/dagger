/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.isPrivate;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import java.util.Optional;

/** A binding for a {@link BindingKind#MEMBERS_INJECTION}. */
@AutoValue
public abstract class MembersInjectionBinding extends Binding {
  @Override
  public final Optional<XElement> bindingElement() {
    return Optional.of(membersInjectedType());
  }

  public final XTypeElement membersInjectedType() {
    return key().type().xprocessing().getTypeElement();
  }

  @Override
  public Optional<XTypeElement> contributingModule() {
    return Optional.empty();
  }

  /** The set of individual sites where {@link Inject} is applied. */
  public abstract ImmutableSortedSet<InjectionSite> injectionSites();

  @Override
  public Optional<BindingType> optionalBindingType() {
    return Optional.of(BindingType.MEMBERS_INJECTION);
  }

  @Override
  public BindingKind kind() {
    return BindingKind.MEMBERS_INJECTION;
  }

  @Override
  public boolean isNullable() {
    return false;
  }

  @Override
  public final ImmutableSet<DependencyRequest> dependencies() {
    return injectionSites().stream()
        .flatMap(injectionSite -> injectionSite.dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * Returns {@code true} if any of this binding's injection sites are directly on the bound type.
   */
  public boolean hasLocalInjectionSites() {
    return injectionSites().stream()
        .map(InjectionSite::enclosingTypeElement)
        .anyMatch(membersInjectedType()::equals);
  }

  @Override
  public boolean requiresModuleInstance() {
    return false;
  }

  @Override
  public Optional<Scope> scope() {
    return Optional.empty();
  }

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  static Builder builder() {
    return new AutoValue_MembersInjectionBinding.Builder();
  }

  /** A {@link MembersInjectionBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder key(Key key);

    abstract Builder unresolved(Optional<? extends Binding> unresolved);

    abstract Builder injectionSites(ImmutableSortedSet<InjectionSite> injectionSites);

    abstract MembersInjectionBinding build();
  }

  /** Metadata about a field or method injection site. */
  @AutoValue
  public abstract static class InjectionSite {
    /** The type of injection site. */
    public enum Kind {
      FIELD,
      METHOD,
    }

    public abstract Kind kind();

    public abstract XElement element();

    public abstract XTypeElement enclosingTypeElement();

    public abstract ImmutableSet<DependencyRequest> dependencies();

    /**
     * Returns the index of {@link #element()} in its parents {@code @Inject} members that have the
     * same simple name. This method filters out private elements so that the results will be
     * consistent independent of whether the build system uses header jars or not.
     */
    @Memoized
    public int indexAmongAtInjectMembersWithSameSimpleName() {
      return enclosingTypeElement().getEnclosedElements().stream()
          .filter(InjectionAnnotations::hasInjectAnnotation)
          .filter(element -> !isPrivate(element))
          .filter(element -> getSimpleName(element).equals(getSimpleName(this.element())))
          .collect(toImmutableList())
          .indexOf(element());
    }

    public static InjectionSite field(XFieldElement field, DependencyRequest dependency) {
      return create(Kind.FIELD, field, ImmutableSet.of(dependency));
    }

    public static InjectionSite method(
        XMethodElement method, Iterable<DependencyRequest> dependencies) {
      return create(Kind.METHOD, method, ImmutableSet.copyOf(dependencies));
    }

    private static InjectionSite create(
        Kind kind, XElement element, ImmutableSet<DependencyRequest> dependencies) {
      XTypeElement enclosingTypeElement = checkNotNull(closestEnclosingTypeElement(element));
      return new AutoValue_MembersInjectionBinding_InjectionSite(
          kind, element, enclosingTypeElement, dependencies);
    }
  }
}
