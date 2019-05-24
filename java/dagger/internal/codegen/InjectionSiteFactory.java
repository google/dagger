/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.MembersInjectionBinding.InjectionSite.Kind.METHOD;
import static dagger.internal.codegen.langmodel.DaggerElements.DECLARATION_ORDER;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor8;

/** A factory for {@link Binding} objects. */
final class InjectionSiteFactory {
  private final ElementVisitor<Optional<InjectionSite>, DeclaredType> injectionSiteVisitor =
      new ElementKindVisitor8<Optional<InjectionSite>, DeclaredType>(Optional.empty()) {
        @Override
        public Optional<InjectionSite> visitExecutableAsMethod(
            ExecutableElement method, DeclaredType type) {
          ExecutableType resolved = MoreTypes.asExecutable(types.asMemberOf(type, method));
          return Optional.of(
              InjectionSite.method(
                  method,
                  dependencyRequestFactory.forRequiredResolvedVariables(
                      method.getParameters(), resolved.getParameterTypes())));
        }

        @Override
        public Optional<InjectionSite> visitVariableAsField(
            VariableElement field, DeclaredType type) {
          if (!isAnnotationPresent(field, Inject.class)
              || field.getModifiers().contains(PRIVATE)
              || field.getModifiers().contains(STATIC)) {
            return Optional.empty();
          }
          TypeMirror resolved = types.asMemberOf(type, field);
          return Optional.of(
              InjectionSite.field(
                  field, dependencyRequestFactory.forRequiredResolvedVariable(field, resolved)));
        }
      };

  private final DaggerTypes types;
  private final DaggerElements elements;
  private final DependencyRequestFactory dependencyRequestFactory;

  @Inject
  InjectionSiteFactory(
      DaggerTypes types,
      DaggerElements elements,
      DependencyRequestFactory dependencyRequestFactory) {
    this.types = types;
    this.elements = elements;
    this.dependencyRequestFactory = dependencyRequestFactory;
  }

  /** Returns the injection sites for a type. */
  ImmutableSortedSet<InjectionSite> getInjectionSites(DeclaredType declaredType) {
    Set<InjectionSite> injectionSites = new HashSet<>();
    List<TypeElement> ancestors = new ArrayList<>();
    SetMultimap<String, ExecutableElement> overriddenMethodMap = LinkedHashMultimap.create();
    for (Optional<DeclaredType> currentType = Optional.of(declaredType);
        currentType.isPresent();
        currentType = types.nonObjectSuperclass(currentType.get())) {
      DeclaredType type = currentType.get();
      ancestors.add(MoreElements.asType(type.asElement()));
      for (Element enclosedElement : type.asElement().getEnclosedElements()) {
        Optional<InjectionSite> maybeInjectionSite =
            injectionSiteVisitor.visit(enclosedElement, type);
        if (maybeInjectionSite.isPresent()) {
          InjectionSite injectionSite = maybeInjectionSite.get();
          if (shouldBeInjected(injectionSite.element(), overriddenMethodMap)) {
            injectionSites.add(injectionSite);
          }
          if (injectionSite.kind().equals(METHOD)) {
            ExecutableElement injectionSiteMethod =
                MoreElements.asExecutable(injectionSite.element());
            overriddenMethodMap.put(
                injectionSiteMethod.getSimpleName().toString(), injectionSiteMethod);
          }
        }
      }
    }
    return ImmutableSortedSet.copyOf(
        // supertypes before subtypes
        Comparator.comparing(
                (InjectionSite injectionSite) ->
                    ancestors.indexOf(injectionSite.element().getEnclosingElement()))
            .reversed()
            // fields before methods
            .thenComparing(injectionSite -> injectionSite.element().getKind())
            // then sort by whichever element comes first in the parent
            // this isn't necessary, but makes the processor nice and predictable
            .thenComparing(InjectionSite::element, DECLARATION_ORDER),
        injectionSites);
  }

  private boolean shouldBeInjected(
      Element injectionSite, SetMultimap<String, ExecutableElement> overriddenMethodMap) {
    if (!isAnnotationPresent(injectionSite, Inject.class)
        || injectionSite.getModifiers().contains(PRIVATE)
        || injectionSite.getModifiers().contains(STATIC)) {
      return false;
    }

    if (injectionSite.getKind().isField()) { // Inject all fields (self and ancestors)
      return true;
    }

    // For each method with the same name belonging to any descendant class, return false if any
    // method has already overridden the injectionSite method. To decrease the number of methods
    // that are checked, we store the already injected methods in a SetMultimap and only
    // check the methods with the same name.
    ExecutableElement injectionSiteMethod = MoreElements.asExecutable(injectionSite);
    TypeElement injectionSiteType = MoreElements.asType(injectionSite.getEnclosingElement());
    for (ExecutableElement method :
        overriddenMethodMap.get(injectionSiteMethod.getSimpleName().toString())) {
      if (elements.overrides(method, injectionSiteMethod, injectionSiteType)) {
        return false;
      }
    }
    return true;
  }
}
