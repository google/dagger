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

package dagger.internal.codegen.binding;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.langmodel.DaggerElements.DECLARATION_ORDER;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor8;

/** A factory for {@link Binding} objects. */
final class InjectionSiteFactory {

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
    InjectionSiteVisitor injectionSiteVisitor = new InjectionSiteVisitor();
    for (Optional<DeclaredType> currentType = Optional.of(declaredType);
        currentType.isPresent();
        currentType = types.nonObjectSuperclass(currentType.get())) {
      DeclaredType type = currentType.get();
      ancestors.add(MoreElements.asType(type.asElement()));
      for (Element enclosedElement : type.asElement().getEnclosedElements()) {
        injectionSiteVisitor.visit(enclosedElement, type).ifPresent(injectionSites::add);
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

  private final class InjectionSiteVisitor
      extends ElementKindVisitor8<Optional<InjectionSite>, DeclaredType> {
    private final SetMultimap<String, ExecutableElement> subclassMethodMap =
        LinkedHashMultimap.create();

    InjectionSiteVisitor() {
      super(Optional.empty());
    }

    @Override
    public Optional<InjectionSite> visitExecutableAsMethod(
        ExecutableElement method, DeclaredType type) {
      subclassMethodMap.put(method.getSimpleName().toString(), method);
      if (!shouldBeInjected(method)) {
        return Optional.empty();
      }
      // This visitor assumes that subclass methods are visited before superclass methods, so we can
      // skip any overridden method that has already been visited. To decrease the number of methods
      // that are checked, we store the already injected methods in a SetMultimap and only check the
      // methods with the same name.
      String methodName = method.getSimpleName().toString();
      TypeElement enclosingType = MoreElements.asType(method.getEnclosingElement());
      for (ExecutableElement subclassMethod : subclassMethodMap.get(methodName)) {
        if (method != subclassMethod && elements.overrides(subclassMethod, method, enclosingType)) {
          return Optional.empty();
        }
      }
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
      if (!shouldBeInjected(field)) {
        return Optional.empty();
      }
      TypeMirror resolved = types.asMemberOf(type, field);
      return Optional.of(
          InjectionSite.field(
              field, dependencyRequestFactory.forRequiredResolvedVariable(field, resolved)));
    }

    private boolean shouldBeInjected(Element injectionSite) {
      return isAnnotationPresent(injectionSite, Inject.class)
          && !injectionSite.getModifiers().contains(PRIVATE)
          && !injectionSite.getModifiers().contains(STATIC);
    }
  }
}
