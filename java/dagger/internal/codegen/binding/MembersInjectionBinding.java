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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Represents the full members injection of a particular type. */
@AutoValue
public abstract class MembersInjectionBinding extends Binding {
  @Override
  public final Optional<Element> bindingElement() {
    return Optional.of(membersInjectedType());
  }

  public abstract TypeElement membersInjectedType();

  @Override
  public abstract Optional<MembersInjectionBinding> unresolved();

  @Override
  public Optional<TypeElement> contributingModule() {
    return Optional.empty();
  }

  /** The set of individual sites where {@link Inject} is applied. */
  public abstract ImmutableSortedSet<InjectionSite> injectionSites();

  @Override
  public BindingType bindingType() {
    return BindingType.MEMBERS_INJECTION;
  }

  @Override
  public BindingKind kind() {
    return BindingKind.MEMBERS_INJECTION;
  }

  @Override
  public boolean isNullable() {
    return false;
  }

  /**
   * Returns {@code true} if any of this binding's injection sites are directly on the bound type.
   */
  public boolean hasLocalInjectionSites() {
    return injectionSites()
        .stream()
        .anyMatch(
            injectionSite ->
                injectionSite.element().getEnclosingElement().equals(membersInjectedType()));
  }

  @Override
  public boolean requiresModuleInstance() {
    return false;
  }

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  /** Metadata about a field or method injection site. */
  @AutoValue
  public abstract static class InjectionSite {
    /** The type of injection site. */
    public enum Kind {
      FIELD,
      METHOD,
    }

    public abstract Kind kind();

    public abstract Element element();

    public abstract ImmutableSet<DependencyRequest> dependencies();

    /**
     * Returns the index of {@link #element()} in its parents {@code @Inject} members that have the
     * same simple name. This method filters out private elements so that the results will be
     * consistent independent of whether the build system uses header jars or not.
     */
    @Memoized
    public int indexAmongAtInjectMembersWithSameSimpleName() {
      return element()
          .getEnclosingElement()
          .getEnclosedElements()
          .stream()
          .filter(element -> isAnnotationPresent(element, Inject.class))
          .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE))
          .filter(element -> element.getSimpleName().equals(this.element().getSimpleName()))
          .collect(toList())
          .indexOf(element());
    }

    public static InjectionSite field(VariableElement element, DependencyRequest dependency) {
      return new AutoValue_MembersInjectionBinding_InjectionSite(
          Kind.FIELD, element, ImmutableSet.of(dependency));
    }

    public static InjectionSite method(
        ExecutableElement element, Iterable<DependencyRequest> dependencies) {
      return new AutoValue_MembersInjectionBinding_InjectionSite(
          Kind.METHOD, element, ImmutableSet.copyOf(dependencies));
    }
  }
}
