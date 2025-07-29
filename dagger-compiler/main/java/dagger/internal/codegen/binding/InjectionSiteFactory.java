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

import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.javacOverrides;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.nonObjectSuperclass;
import static java.util.Comparator.comparing;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** A factory for {@link Binding} objects. */
final class InjectionSiteFactory {
  private final DependencyRequestFactory dependencyRequestFactory;

  @Inject
  InjectionSiteFactory(DependencyRequestFactory dependencyRequestFactory) {
    this.dependencyRequestFactory = dependencyRequestFactory;
  }

  /** Returns the injection sites for a type. */
  ImmutableSortedSet<InjectionSite> getInjectionSites(XType type) {
    checkArgument(isDeclared(type));
    Set<InjectionSite> injectionSites = new HashSet<>();
    InjectionSiteVisitor injectionSiteVisitor = new InjectionSiteVisitor();
    Map<XTypeElement, Integer> enclosingTypeElementOrder = new HashMap<>();
    Map<XElement, Integer> enclosedElementOrder = new HashMap<>();
    for (Optional<XType> currentType = Optional.of(type);
        currentType.isPresent();
        currentType = nonObjectSuperclass(currentType.get())) {
      XTypeElement typeElement = currentType.get().getTypeElement();
      enclosingTypeElementOrder.put(typeElement, enclosingTypeElementOrder.size());
      for (XElement enclosedElement : typeElement.getEnclosedElements()) {
        injectionSiteVisitor
            .visit(enclosedElement, currentType.get())
            .ifPresent(
                injectionSite -> {
                  enclosedElementOrder.put(enclosedElement, enclosedElementOrder.size());
                  injectionSites.add(injectionSite);
                });
      }
    }
    return ImmutableSortedSet.copyOf(
        // supertypes before subtypes
        comparing(
                InjectionSite::enclosingTypeElement,
                comparing(enclosingTypeElementOrder::get).reversed())
            // fields before methods
            .thenComparing(InjectionSite::kind)
            // then sort by whichever element comes first in the parent
            // this isn't necessary, but makes the processor nice and predictable
            .thenComparing(InjectionSite::element, comparing(enclosedElementOrder::get)),
        injectionSites);
  }

  private final class InjectionSiteVisitor {
    private final SetMultimap<String, XMethodElement> subclassMethodMap =
        LinkedHashMultimap.create();

    public Optional<InjectionSite> visit(XElement element, XType container) {
      if (isMethod(element)) {
        return visitMethod(asMethod(element), container);
      } else if (isField(element)) {
        return visitField(asField(element), container);
      }
      return Optional.empty();
    }

    public Optional<InjectionSite> visitMethod(XMethodElement method, XType container) {
      subclassMethodMap.put(getSimpleName(method), method);
      if (!shouldBeInjected(method)) {
        return Optional.empty();
      }
      // This visitor assumes that subclass methods are visited before superclass methods, so we can
      // skip any overridden method that has already been visited. To decrease the number of methods
      // that are checked, we store the already injected methods in a SetMultimap and only check the
      // methods with the same name.
      XTypeElement enclosingType = closestEnclosingTypeElement(method);
      for (XMethodElement subclassMethod : subclassMethodMap.get(getSimpleName(method))) {
        if (method != subclassMethod && javacOverrides(subclassMethod, method, enclosingType)) {
          return Optional.empty();
        }
      }
      XMethodType resolved = method.asMemberOf(container);
      return Optional.of(
          InjectionSite.method(
              method,
              dependencyRequestFactory.forRequiredResolvedVariables(
                  method.getParameters(), resolved.getParameterTypes())));
    }

    public Optional<InjectionSite> visitField(XFieldElement field, XType container) {
      if (!shouldBeInjected(field)) {
        return Optional.empty();
      }
      XType resolved = field.asMemberOf(container);
      return Optional.of(
          InjectionSite.field(
              field, dependencyRequestFactory.forRequiredResolvedVariable(field, resolved)));
    }

    private boolean shouldBeInjected(XElement injectionSite) {
      return InjectionAnnotations.hasInjectAnnotation(injectionSite)
          && !XElements.isPrivate(injectionSite)
          && !XElements.isStatic(injectionSite);
    }
  }
}
