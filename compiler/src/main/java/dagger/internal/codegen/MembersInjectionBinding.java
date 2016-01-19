/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Represents the full members injection of a particular type.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class MembersInjectionBinding extends Binding {
  @Override
  abstract Optional<MembersInjectionBinding> unresolved();

  @Override
  TypeElement bindingElement() {
    return MoreElements.asType(super.bindingElement());
  }

  @Override
  Set<DependencyRequest> implicitDependencies() {
    return dependencies();
  }

  /** The set of individual sites where {@link Inject} is applied. */
  abstract ImmutableSortedSet<InjectionSite> injectionSites();

  /**
   * The {@link Key} for the non-object superclass of {@link #bindingElement()}. Absent if
   * {@link #bindingElement()} is a direct subclass of {@link Object}. 
   */
  abstract Optional<Key> parentKey();

  enum Strategy {
    NO_OP,
    INJECT_MEMBERS,
  }

  Strategy injectionStrategy() {
    return injectionSites().isEmpty() ? Strategy.NO_OP : Strategy.INJECT_MEMBERS;
  }

  @Override
  public BindingType bindingType() {
    return BindingType.MEMBERS_INJECTION;
  }

  /**
   * Returns {@code true} if any of this binding's injection sites are directly on the bound type.
   */
  boolean hasLocalInjectionSites() {
    return FluentIterable.from(injectionSites())
        .anyMatch(
            new Predicate<InjectionSite>() {
              @Override
              public boolean apply(InjectionSite injectionSite) {
                return injectionSite.element().getEnclosingElement().equals(bindingElement());
              }
            });
  }

  @AutoValue
  abstract static class InjectionSite {
    enum Kind {
      FIELD,
      METHOD,
    }

    abstract Kind kind();

    abstract Element element();

    abstract ImmutableSet<DependencyRequest> dependencies();
    
    protected int indexAmongSiblingMembers(InjectionSite injectionSite) {
      return injectionSite
          .element()
          .getEnclosingElement()
          .getEnclosedElements()
          .indexOf(injectionSite.element());
    }
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = checkNotNull(elements);
      this.types = checkNotNull(types);
      this.keyFactory = checkNotNull(keyFactory);
      this.dependencyRequestFactory = checkNotNull(dependencyRequestFactory);
    }

    private InjectionSite injectionSiteForInjectMethod(
        ExecutableElement methodElement, DeclaredType containingType) {
      checkNotNull(methodElement);
      checkArgument(methodElement.getKind().equals(ElementKind.METHOD));
      ExecutableType resolved =
          MoreTypes.asExecutable(types.asMemberOf(containingType, methodElement));
      return new AutoValue_MembersInjectionBinding_InjectionSite(
          InjectionSite.Kind.METHOD,
          methodElement,
          dependencyRequestFactory.forRequiredResolvedVariables(
              containingType, methodElement.getParameters(), resolved.getParameterTypes()));
    }

    private InjectionSite injectionSiteForInjectField(
        VariableElement fieldElement, DeclaredType containingType) {
      checkNotNull(fieldElement);
      checkArgument(fieldElement.getKind().equals(ElementKind.FIELD));
      checkArgument(isAnnotationPresent(fieldElement, Inject.class));
      TypeMirror resolved = types.asMemberOf(containingType, fieldElement);
      return new AutoValue_MembersInjectionBinding_InjectionSite(
          InjectionSite.Kind.FIELD,
          fieldElement,
          ImmutableSet.of(
              dependencyRequestFactory.forRequiredResolvedVariable(
                  containingType, fieldElement, resolved)));
    }

    /** Returns true if the type has some injected members in itself or any of its super classes. */
    boolean hasInjectedMembers(DeclaredType declaredType) {
      return !getInjectionSites(declaredType).isEmpty();
    }

    /**
     * Returns a MembersInjectionBinding for the given type. If {@code resolvedType} is present,
     * this will return a resolved binding, with the key & type resolved to the given type (using
     * {@link Types#asMemberOf(DeclaredType, Element)}).
     */
    MembersInjectionBinding forInjectedType(
        DeclaredType declaredType, Optional<TypeMirror> resolvedType) {
      // If the class this is injecting has some type arguments, resolve everything.
      if (!declaredType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
        DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
        // Validate that we're resolving from the correct type.
        checkState(
            types.isSameType(types.erasure(resolved), types.erasure(declaredType)),
            "erased expected type: %s, erased actual type: %s",
            types.erasure(resolved),
            types.erasure(declaredType));
        declaredType = resolved;
      }
      ImmutableSortedSet<InjectionSite> injectionSites = getInjectionSites(declaredType);
      ImmutableSet<DependencyRequest> dependencies =
          FluentIterable.from(injectionSites)
              .transformAndConcat(
                  new Function<InjectionSite, Set<DependencyRequest>>() {
                    @Override
                    public Set<DependencyRequest> apply(InjectionSite input) {
                      return input.dependencies();
                    }
                  })
              .toSet();

      Optional<Key> parentKey =
          MoreTypes.nonObjectSuperclass(types, elements, declaredType)
              .transform(
                  new Function<DeclaredType, Key>() {
                    @Override
                    public Key apply(DeclaredType superclass) {
                      return keyFactory.forMembersInjectedType(superclass);
                    }
                  });

      Key key = keyFactory.forMembersInjectedType(declaredType);
      TypeElement typeElement = MoreElements.asType(declaredType.asElement());
      return new AutoValue_MembersInjectionBinding(
          SourceElement.forElement(typeElement),
          key,
          dependencies,
          findBindingPackage(key),
          hasNonDefaultTypeParameters(typeElement, key.type(), types)
              ? Optional.of(
                  forInjectedType(
                      MoreTypes.asDeclared(typeElement.asType()), Optional.<TypeMirror>absent()))
              : Optional.<MembersInjectionBinding>absent(),
          injectionSites,
          parentKey);
    }

    private ImmutableSortedSet<InjectionSite> getInjectionSites(DeclaredType declaredType) {
      Set<InjectionSite> injectionSites = new HashSet<>();
      final List<TypeElement> ancestors = new ArrayList<>();
      SetMultimap<String, ExecutableElement> overriddenMethodMap = LinkedHashMultimap.create();
      for (Optional<DeclaredType> currentType = Optional.of(declaredType);
          currentType.isPresent();
          currentType = MoreTypes.nonObjectSuperclass(types, elements, currentType.get())) {
        final DeclaredType type = currentType.get();
        ancestors.add(MoreElements.asType(type.asElement()));
        for (Element enclosedElement : type.asElement().getEnclosedElements()) {
          Optional<InjectionSite> maybeInjectionSite =
              injectionSiteVisitor.visit(enclosedElement, type);
          if (maybeInjectionSite.isPresent()) {
            InjectionSite injectionSite = maybeInjectionSite.get();
            if (shouldBeInjected(injectionSite.element(), overriddenMethodMap)) {
              injectionSites.add(injectionSite);
            }
            if (injectionSite.kind() == InjectionSite.Kind.METHOD) {
              ExecutableElement injectionSiteMethod =
                  MoreElements.asExecutable(injectionSite.element());
              overriddenMethodMap.put(
                  injectionSiteMethod.getSimpleName().toString(), injectionSiteMethod);
            }
          }
        }
      }
      return ImmutableSortedSet.copyOf(
          new Comparator<InjectionSite>() {
            @Override
            public int compare(InjectionSite left, InjectionSite right) {
              return ComparisonChain.start()
                  // supertypes before subtypes
                  .compare(
                      ancestors.indexOf(right.element().getEnclosingElement()),
                      ancestors.indexOf(left.element().getEnclosingElement()))
                  // fields before methods
                  .compare(left.element().getKind(), right.element().getKind())
                  // then sort by whichever element comes first in the parent
                  // this isn't necessary, but makes the processor nice and predictable
                  .compare(
                      left.indexAmongSiblingMembers(left), right.indexAmongSiblingMembers(right))
                  .result();
            }
          },
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

    private final ElementVisitor<Optional<InjectionSite>, DeclaredType> injectionSiteVisitor =
        new ElementKindVisitor6<Optional<InjectionSite>, DeclaredType>(
            Optional.<InjectionSite>absent()) {
          @Override
          public Optional<InjectionSite> visitExecutableAsMethod(
              ExecutableElement e, DeclaredType type) {
            return Optional.of(injectionSiteForInjectMethod(e, type));
          }

          @Override
          public Optional<InjectionSite> visitVariableAsField(
              VariableElement e, DeclaredType type) {
            return (isAnnotationPresent(e, Inject.class)
                    && !e.getModifiers().contains(PRIVATE)
                    && !e.getModifiers().contains(STATIC))
                ? Optional.of(injectionSiteForInjectField(e, type))
                : Optional.<InjectionSite>absent();
          }
        };
  }
}
