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

package dagger.internal.codegen.langmodel;

import static com.google.auto.common.MoreElements.getPackage;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreElements;
import java.util.Optional;
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
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Utility methods for determining whether a {@linkplain TypeMirror type} or an {@linkplain Element
 * element} is accessible given the rules outlined in <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6">section 6.6 of the
 * Java Language Specification</a>.
 *
 * <p>This class only provides an approximation for accessibility. It does not always yield the same
 * result as the compiler, but will always err on the side of declaring something inaccessible. This
 * ensures that using this class will never result in generating code that will not compile.
 *
 * <p>Whenever compiler independence is not a requirement, the compiler-specific implementation of
 * this functionality should be preferred. For example, {@link
 * com.sun.source.util.Trees#isAccessible(com.sun.source.tree.Scope, TypeElement)} would be
 * preferable for {@code javac}.
 */
public final class Accessibility {
  /** Returns true if the given type can be referenced from any package. */
  public static boolean isTypePubliclyAccessible(TypeMirror type) {
    return type.accept(new TypeAccessibilityVisitor(), null);
  }

  /** Returns true if the given type can be referenced from code in the given package. */
  public static boolean isTypeAccessibleFrom(TypeMirror type, String packageName) {
    return type.accept(new TypeAccessibilityVisitor(packageName), null);
  }

  private static boolean isTypeAccessibleFrom(TypeMirror type, Optional<String> packageName) {
    return type.accept(new TypeAccessibilityVisitor(packageName), null);
  }

  private static final class TypeAccessibilityVisitor extends SimpleTypeVisitor6<Boolean, Void> {
    final Optional<String> packageName;

    TypeAccessibilityVisitor() {
      this(Optional.empty());
    }

    TypeAccessibilityVisitor(String packageName) {
      this(Optional.of(packageName));
    }

    TypeAccessibilityVisitor(Optional<String> packageName) {
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
      throw new IllegalArgumentException(
          String.format(
              "%s of kind %s should not be checked for accessibility", type, type.getKind()));
    }
  }

  /** Returns true if the given element can be referenced from any package. */
  public static boolean isElementPubliclyAccessible(Element element) {
    return element.accept(new ElementAccessibilityVisitor(), null);
  }

  /** Returns true if the given element can be referenced from code in the given package. */
  // TODO(gak): account for protected
  public static boolean isElementAccessibleFrom(Element element, String packageName) {
    return element.accept(new ElementAccessibilityVisitor(packageName), null);
  }

  private static boolean isElementAccessibleFrom(Element element, Optional<String> packageName) {
    return element.accept(new ElementAccessibilityVisitor(packageName), null);
  }

  /** Returns true if the given element can be referenced from other code in its own package. */
  public static boolean isElementAccessibleFromOwnPackage(Element element) {
    return isElementAccessibleFrom(
        element, MoreElements.getPackage(element).getQualifiedName().toString());
  }

  private static final class ElementAccessibilityVisitor
      extends SimpleElementVisitor6<Boolean, Void> {
    final Optional<String> packageName;

    ElementAccessibilityVisitor() {
      this(Optional.empty());
    }

    ElementAccessibilityVisitor(String packageName) {
      this(Optional.of(packageName));
    }

    ElementAccessibilityVisitor(Optional<String> packageName) {
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
      }
      throw new AssertionError();
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
      } else if (packageName.isPresent()
          && getPackage(element).getQualifiedName().contentEquals(packageName.get())) {
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

  private static final TypeVisitor<Boolean, Optional<String>> RAW_TYPE_ACCESSIBILITY_VISITOR =
      new SimpleTypeVisitor8<Boolean, Optional<String>>() {
        @Override
        protected Boolean defaultAction(TypeMirror e, Optional<String> requestingPackage) {
          return isTypeAccessibleFrom(e, requestingPackage);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Optional<String> requestingPackage) {
          return isElementAccessibleFrom(t.asElement(), requestingPackage);
        }
      };

  /** Returns true if the raw type of {@code type} is accessible from the given package. */
  public static boolean isRawTypeAccessible(TypeMirror type, String requestingPackage) {
    return type.accept(RAW_TYPE_ACCESSIBILITY_VISITOR, Optional.of(requestingPackage));
  }

  /** Returns true if the raw type of {@code type} is accessible from any package. */
  public static boolean isRawTypePubliclyAccessible(TypeMirror type) {
    return type.accept(RAW_TYPE_ACCESSIBILITY_VISITOR, Optional.empty());
  }

  private Accessibility() {}
}
