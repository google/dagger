/*
 * Copyright (C) 2015 Google, Inc.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.google.auto.common.MoreElements.getPackage;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Utility methods for determining whether a {@linkplain TypeMirror type} or an {@linkplain Element
 * element} is accessible given the rules outlined in
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6">section 6.6 of the
 * Java Language Specification</a>.
 *
 * <p>This class only provides an approximation for accessibility.  It does not always yield the
 * same result as the compiler, but will always err on the side of declaring something inaccessible.
 * This ensures that using this class will never result in generating code that will not compile.
 *
 * <p>Whenever compiler independence is not a requirement, the compiler-specific implementation of
 * this functionality should be preferred.  For example,
 * {@link com.sun.source.util.Trees#isAccessible(com.sun.source.tree.Scope, TypeElement)} would be
 * preferable for {@code javac}.
 */
final class Accessibility {
  /**
   * Returns true if the given type can be referenced from code in the given package.
   */
  static boolean isTypeAccessibleFrom(TypeMirror type, String packageName) {
    return type.accept(new TypeAccessiblityVisitor(packageName), null);
  }

  private static final class TypeAccessiblityVisitor extends SimpleTypeVisitor6<Boolean, Void> {
    final String packageName;

    TypeAccessiblityVisitor(String packageName) {
      this.packageName = packageName;
    }

    boolean isAccessible(TypeMirror type) {
      return type.accept(this, null);
    }

    @Override
    public Boolean visitNoType(NoType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitDeclared(DeclaredType type, Void p) {
      if (!isAccessible(type.getEnclosingType())) {
        // TODO(gak): investigate this check.  see comment in Binding
        return false;
      }
      if (!isElementAccessibleFrom(type.asElement(), packageName)) {
        return false;
      }
      for (TypeMirror typeArgument : type.getTypeArguments()) {
        if (!isAccessible(typeArgument)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Boolean visitArray(ArrayType type, Void p) {
      return type.getComponentType().accept(this, null);
    }

    @Override
    public Boolean visitPrimitive(PrimitiveType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitNull(NullType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitTypeVariable(TypeVariable type, Void p) {
      // a _reference_ to a type variable is always accessible
      return true;
    }

    @Override
    public Boolean visitWildcard(WildcardType type, Void p) {
      if (type.getExtendsBound() != null && !isAccessible(type.getExtendsBound())) {
        return false;
      }
      if (type.getSuperBound() != null && !isAccessible(type.getSuperBound())) {
        return false;
      }
      return true;
    }

    @Override
    protected Boolean defaultAction(TypeMirror type, Void p) {
      throw new IllegalArgumentException(String.format(
          "%s of kind %s should not be checked for accessibility", type, type.getKind()));
    }
  }

  /**
   * Returns true if the given element can be referenced from code in the given package.
   */
  //TODO(gak): account for protected
  static boolean isElementAccessibleFrom(Element element, final String packageName) {
    return element.accept(new ElementAccessibilityVisitor(packageName), null);
  }

  private static final class ElementAccessibilityVisitor
      extends SimpleElementVisitor6<Boolean, Void> {
    final String packageName;

    ElementAccessibilityVisitor(String packageName) {
      this.packageName = packageName;
    }

    @Override
    public Boolean visitPackage(PackageElement element, Void p) {
      return true;
    }

    @Override
    public Boolean visitType(TypeElement element, Void p) {
      switch (element.getNestingKind()) {
        case MEMBER:
          return accessibleMember(element);
        case TOP_LEVEL:
          return accessibleModifiers(element);
        case ANONYMOUS:
        case LOCAL:
          return false;
        default:
          throw new AssertionError();
      }
    }

    boolean accessibleMember(Element element) {
      if (!element.getEnclosingElement().accept(this, null)) {
        return false;
      }
      return accessibleModifiers(element);
    }

    boolean accessibleModifiers(Element element) {
      if (element.getModifiers().contains(PUBLIC)) {
        return true;
      } else if (element.getModifiers().contains(PRIVATE)) {
        return false;
      } else if (getPackage(element).getQualifiedName().contentEquals(packageName)) {
        return true;
      } else {
        return false;
      }
    }

    @Override
    public Boolean visitTypeParameter(TypeParameterElement element, Void p) {
      throw new IllegalArgumentException(
          "It does not make sense to check the accessibility of a type parameter");
    }

    @Override
    public Boolean visitExecutable(ExecutableElement element, Void p) {
      return accessibleMember(element);
    }

    @Override
    public Boolean visitVariable(VariableElement element, Void p) {
      ElementKind kind = element.getKind();
      checkArgument(kind.isField(), "checking a variable that isn't a field: %s", kind);
      return accessibleMember(element);
    }
  }

  private Accessibility() {}
}

