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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.Key.HasKey;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * An abstract type for classes representing a Dagger binding.  Particularly, contains the
 * {@link Element} that generated the binding and the {@link DependencyRequest} instances that are
 * required to satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding
 * to the subtypes.
 *
 * @author Gregory Kick
 * @since 2.0
 */
abstract class Binding implements HasBindingType, HasKey, HasSourceElement {

  /**
   * Returns the framework class associated with this binding.
   */
  Class<?> frameworkClass() {
    return bindingType().frameworkClass();
  }

  /** The {@link Key} that is provided by this binding. */
  @Override
  public abstract Key key();

  /** Returns the {@link Element} instance that is responsible for declaring the binding. */
  Element bindingElement() {
    return sourceElement().element();
  }

  /** The type enclosing the binding {@link #bindingElement()}. */
  TypeElement bindingTypeElement() {
    return sourceElement().enclosingTypeElement();
  }

  /**
   * The explicit set of {@link DependencyRequest dependencies} required to satisfy this binding.
   */
  abstract ImmutableSet<DependencyRequest> dependencies();

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding. This is a
   * superset of {@link #dependencies()}.  This returns an unmodifiable set.
   */
  abstract Set<DependencyRequest> implicitDependencies();

  /**
   * Returns the name of the package in which this binding must be managed. E.g.: a binding
   * may reference non-public types.
   */
  abstract Optional<String> bindingPackage();

  protected static Optional<String> findBindingPackage(Key bindingKey) {
    Set<String> packages = nonPublicPackageUse(bindingKey.type());
    switch (packages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(packages.iterator().next());
      default:
        throw new IllegalStateException();
    }
  }

  private static Set<String> nonPublicPackageUse(TypeMirror typeMirror) {
    ImmutableSet.Builder<String> packages = ImmutableSet.builder();
    typeMirror.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<String>>() {
      @Override
      public Void visitArray(ArrayType t, ImmutableSet.Builder<String> p) {
        return t.getComponentType().accept(this, p);
      }

      @Override
      public Void visitDeclared(DeclaredType t, ImmutableSet.Builder<String> p) {
        for (TypeMirror typeArgument : t.getTypeArguments()) {
          typeArgument.accept(this, p);
        }
        // TODO(gak): address public nested types in non-public types
        TypeElement typeElement = MoreElements.asType(t.asElement());
        if (!typeElement.getModifiers().contains(PUBLIC)) {
          PackageElement elementPackage = MoreElements.getPackage(typeElement);
          Name qualifiedName = elementPackage.getQualifiedName();
          p.add(qualifiedName.toString());
        }
        // Also make sure enclosing types are visible, otherwise we're fooled by
        // class Foo { public class Bar }
        // (Note: we can't use t.getEnclosingType() because it doesn't work!)
        typeElement.getEnclosingElement().asType().accept(this, p);
        return null;
      }

      @Override
      public Void visitWildcard(WildcardType t, ImmutableSet.Builder<String> p) {
        if (t.getExtendsBound() != null) {
          t.getExtendsBound().accept(this, p);
        }
        if (t.getSuperBound() != null) {
          t.getSuperBound().accept(this, p);
        }
        return null;
      }
    }, packages);
    return packages.build();
  }

  /**
   * if this binding's key's type parameters are different from those of the
   * {@link #bindingTypeElement()}, this is the binding for the {@link #bindingTypeElement()}'s
   * unresolved type.
   */
  abstract Optional<? extends Binding> unresolved();

  /**
   * The scope of this binding.
   */
  Scope scope() {
    return Scope.unscoped();
  }

  // TODO(sameb): Remove the TypeElement parameter and pull it from the TypeMirror.
  static boolean hasNonDefaultTypeParameters(TypeElement element, TypeMirror type, Types types) {
    // If the element has no type parameters, nothing can be wrong.
    if (element.getTypeParameters().isEmpty()) {
      return false;
    }

    List<TypeMirror> defaultTypes = Lists.newArrayList();
    for (TypeParameterElement parameter : element.getTypeParameters()) {
      defaultTypes.add(parameter.asType());
    }

    List<TypeMirror> actualTypes =
        type.accept(
            new SimpleTypeVisitor6<List<TypeMirror>, Void>() {
              @Override
              protected List<TypeMirror> defaultAction(TypeMirror e, Void p) {
                return ImmutableList.of();
              }

              @Override
              public List<TypeMirror> visitDeclared(DeclaredType t, Void p) {
                return ImmutableList.<TypeMirror>copyOf(t.getTypeArguments());
              }
            },
            null);

    // The actual type parameter size can be different if the user is using a raw type.
    if (defaultTypes.size() != actualTypes.size()) {
      return true;
    }

    for (int i = 0; i < defaultTypes.size(); i++) {
      if (!types.isSameType(defaultTypes.get(i), actualTypes.get(i))) {
        return true;
      }
    }
    return false;
  }
}
