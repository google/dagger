/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toKS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotated;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XEnumEntry;
import androidx.room.compiler.processing.XEnumTypeElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XHasModifiers;
import androidx.room.compiler.processing.XMemberContainer;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XTypeParameterElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.ksp.symbol.KSAnnotated;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XElement} helper methods. */
public final class XElements {

  // TODO(bcorso): Replace usages with getJvmName() once it exists.
  /** Returns the simple name of the member container. */
  public static String getSimpleName(XMemberContainer memberContainer) {
    return memberContainer.getClassName().simpleName();
  }

  /** Returns the simple name of the element. */
  public static String getSimpleName(XElement element) {
    if (isTypeElement(element)) {
      return asTypeElement(element)
          .getName(); // SUPPRESS_GET_NAME_CHECK: This uses java simple name implementation under
      // the hood.
    } else if (isVariableElement(element)) {
      return asVariable(element).getName(); // SUPPRESS_GET_NAME_CHECK
    } else if (isEnumEntry(element)) {
      return asEnumEntry(element).getName(); // SUPPRESS_GET_NAME_CHECK
    } else if (isMethod(element)) {
      // Note: We use "jvm name" here rather than "simple name" because simple name is not reliable
      // in KAPT. In particular, XProcessing relies on matching the method to its descriptor found
      // in the Kotlin @Metadata to get the simple name. However, this doesn't work for method
      // descriptors that contain generated types because the stub and @Metadata will disagree due
      // to the following bug:
      // https://youtrack.jetbrains.com/issue/KT-35124/KAPT-not-correcting-error-types-in-Kotlin-Metadata-information-produced-for-the-stubs.
      // In any case, always using the jvm name should be safe; however, it will unfortunately
      // contain any obfuscation added by kotlinc, e.g. for "internal" methods, which can make the
      // "simple name" not as nice/short when used for things like error messages or class names.
      return asMethod(element).getJvmName();
    } else if (isConstructor(element)) {
      return "<init>";
    } else if (isTypeParameter(element)) {
      return asTypeParameter(element).getName(); // SUPPRESS_GET_NAME_CHECK
    }
    throw new AssertionError("No simple name for: " + element);
  }

  private static boolean isSyntheticElement(XElement element) {
    if (isMethodParameter(element)) {
      XExecutableParameterElement executableParam = asMethodParameter(element);
      return executableParam.isContinuationParam()
          || executableParam.isReceiverParam()
          || executableParam.isKotlinPropertyParam();
    }
    if (isMethod(element)) {
      return asMethod(element).isKotlinPropertyMethod();
    }
    return false;
  }

  @Nullable
  public static KSAnnotated toKSAnnotated(XElement element) {
    if (isSyntheticElement(element)) {
      return toKS(element);
    }
    if (isExecutable(element)) {
      return toKS(asExecutable(element));
    }
    if (isTypeElement(element)) {
      return toKS(asTypeElement(element));
    }
    if (isField(element)) {
      return toKS(asField(element));
    }
    if (isMethodParameter(element)) {
      return toKS(asMethodParameter(element));
    }
    throw new IllegalStateException(
        "Returning KSAnnotated declaration for " + element + " is not supported.");
  }

  /**
   * Returns the closest enclosing element that is a {@link XTypeElement} or throws an {@link
   * IllegalStateException} if one doesn't exist.
   */
  public static XTypeElement closestEnclosingTypeElement(XElement element) {
    return optionalClosestEnclosingTypeElement(element)
        .orElseThrow(() -> new IllegalStateException("No enclosing TypeElement for: " + element));
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to or transitively encloses {@code enclosed}.
   */
  public static boolean transitivelyEncloses(XElement encloser, XElement enclosed) {
    XElement current = enclosed;
    while (current != null) {
      if (current.equals(encloser)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  private static Optional<XTypeElement> optionalClosestEnclosingTypeElement(XElement element) {
    if (isTypeElement(element)) {
      return Optional.of(asTypeElement(element));
    } else if (isConstructor(element)) {
      return Optional.of(asConstructor(element).getEnclosingElement());
    } else if (isMethod(element)) {
      return optionalClosestEnclosingTypeElement(asMethod(element).getEnclosingElement());
    } else if (isField(element)) {
      return optionalClosestEnclosingTypeElement(asField(element).getEnclosingElement());
    } else if (isMethodParameter(element)) {
      return optionalClosestEnclosingTypeElement(asMethodParameter(element).getEnclosingElement());
    }
    return Optional.empty();
  }

  public static boolean isAbstract(XElement element) {
    return asHasModifiers(element).isAbstract();
  }

  public static boolean isPublic(XElement element) {
    return asHasModifiers(element).isPublic();
  }

  public static boolean isPrivate(XElement element) {
    return asHasModifiers(element).isPrivate();
  }

  public static boolean isInternal(XElement element) {
    return asHasModifiers(element).isInternal();
  }

  public static boolean isStatic(XElement element) {
    return asHasModifiers(element).isStatic();
  }

  // TODO(bcorso): Ideally we would modify XElement to extend XHasModifiers to prevent possible
  // runtime exceptions if the element does not extend XHasModifiers. However, for Dagger's purpose
  // all usages should be on elements that do extend XHasModifiers, so generalizing this for
  // XProcessing is probably overkill for now.
  private static XHasModifiers asHasModifiers(XElement element) {
    // In javac, Element implements HasModifiers but in XProcessing XElement does not.
    // Currently, the elements that do not extend XHasModifiers are XMemberContainer, XEnumEntry,
    // XVariableElement. Though most instances of XMemberContainer will extend XHasModifiers through
    // XTypeElement instead.
    checkArgument(element instanceof XHasModifiers, "Element %s does not have modifiers", element);
    return (XHasModifiers) element;
  }

  // Note: This method always returns `false` but I'd rather not remove it from our codebase since
  // if XProcessing adds package elements to their model I'd like to catch it here and fail early.
  public static boolean isPackage(XElement element) {
    // Currently, XProcessing doesn't represent package elements so this method always returns
    // false, but we check the state in Javac just to be sure. There's nothing to check in KSP since
    // there is no concept of package elements in KSP.
    if (getProcessingEnv(element).getBackend() == XProcessingEnv.Backend.JAVAC) {
      checkState(toJavac(element).getKind() != ElementKind.PACKAGE);
    }
    return false;
  }

  public static boolean isTypeParameter(XElement element) {
    return element instanceof XTypeParameterElement;
  }

  public static XTypeParameterElement asTypeParameter(XElement element) {
    return (XTypeParameterElement) element;
  }

  public static boolean isEnumEntry(XElement element) {
    return element instanceof XEnumEntry;
  }

  public static boolean isEnum(XElement element) {
    return element instanceof XEnumTypeElement;
  }

  public static boolean isExecutable(XElement element) {
    return isConstructor(element) || isMethod(element);
  }

  public static XExecutableElement asExecutable(XElement element) {
    checkState(isExecutable(element));
    return (XExecutableElement) element;
  }

  public static XTypeElement asTypeElement(XElement element) {
    checkState(isTypeElement(element));
    return (XTypeElement) element;
  }

  // TODO(bcorso): Rename this and the XElementKt.isMethodParameter to isExecutableParameter.
  public static XExecutableParameterElement asMethodParameter(XElement element) {
    checkState(isMethodParameter(element));
    return (XExecutableParameterElement) element;
  }

  public static XFieldElement asField(XElement element) {
    checkState(isField(element));
    return (XFieldElement) element;
  }

  public static XEnumEntry asEnumEntry(XElement element) {
    return (XEnumEntry) element;
  }

  public static XVariableElement asVariable(XElement element) {
    checkState(isVariableElement(element));
    return (XVariableElement) element;
  }

  public static XConstructorElement asConstructor(XElement element) {
    checkState(isConstructor(element));
    return (XConstructorElement) element;
  }

  public static XMethodElement asMethod(XElement element) {
    checkState(isMethod(element));
    return (XMethodElement) element;
  }

  public static ImmutableSet<XAnnotation> getAnnotatedAnnotations(
      XAnnotated annotated, XClassName annotationName) {
    return annotated.getAllAnnotations().stream()
        .filter(annotation -> annotation.getType().getTypeElement().hasAnnotation(annotationName))
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, XClassName... annotations) {
    return hasAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, Collection<XClassName> annotations) {
    return annotations.stream().anyMatch(annotated::hasAnnotation);
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else {@code
   * Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, XClassName... annotations) {
    return getAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else {@code
   * Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, Collection<XClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .findFirst();
  }

  /** Returns all annotations from {@code annotations} that annotate {@code annotated}. */
  public static ImmutableSet<XAnnotation> getAllAnnotations(
      XAnnotated annotated, XClassName... annotations) {
    return getAllAnnotations(annotated, ImmutableSet.copyOf(annotations));
  }

  /** Returns all annotations from {@code annotations} that annotate {@code annotated}. */
  public static ImmutableSet<XAnnotation> getAllAnnotations(
      XAnnotated annotated, Collection<XClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .collect(toImmutableSet());
  }

  /**
   * Returns a string representation of {@link XElement} that is independent of the backend
   * (javac/ksp).
   */
  public static String toStableString(XElement element) {
    if (element == null) {
      return "<null>";
    }
    try {
      if (isTypeElement(element)) {
        return asTypeElement(element).getQualifiedName();
      } else if (isExecutable(element)) {
        XExecutableElement executable = asExecutable(element);
        // TODO(b/318709946) resolving ksp types can be expensive, therefore we should avoid it
        // here for extreme cases until ksp improved the performance.
        boolean tooManyParameters =
            getProcessingEnv(element).getBackend().equals(XProcessingEnv.Backend.KSP)
                && executable.getParameters().size() > 10;
        return String.format(
            "%s(%s)",
            getSimpleName(
                isConstructor(element) ? asConstructor(element).getEnclosingElement() : executable),
            (tooManyParameters
                    ? executable.getParameters().stream().limit(10)
                    : executable.getParameters().stream()
                        .map(XExecutableParameterElement::getType)
                        .map(XTypes::toStableString)
                        .collect(joining(",")))
                + (tooManyParameters ? ", ..." : ""));
      } else if (isEnumEntry(element)
                     || isField(element)
                     || isMethodParameter(element)
                     || isTypeParameter(element)) {
        return getSimpleName(element);
      }
      return element.toString();
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  // XElement#kindName() exists, but doesn't give consistent results between JAVAC and KSP (e.g.
  // METHOD vs FUNCTION) so this custom implementation is meant to provide that consistency.
  public static String getKindName(XElement element) {
    if (isTypeElement(element)) {
      XTypeElement typeElement = asTypeElement(element);
      if (typeElement.isClass()) {
        return "CLASS";
      } else if (typeElement.isInterface()) {
        return "INTERFACE";
      } else if (typeElement.isAnnotationClass()) {
        return "ANNOTATION_TYPE";
      }
    } else if (isEnum(element)) {
      return "ENUM";
    } else if (isEnumEntry(element)) {
      return "ENUM_CONSTANT";
    } else if (isConstructor(element)) {
      return "CONSTRUCTOR";
    } else if (isMethod(element)) {
      return "METHOD";
    } else if (isField(element)) {
      return "FIELD";
    } else if (isMethodParameter(element)) {
      return "PARAMETER";
    } else if (isTypeParameter(element)) {
      return "TYPE_PARAMETER";
    }
    return element.kindName();
  }

  public static String packageName(XElement element) {
    return element.getClosestMemberContainer().asClassName().getPackageName();
  }

  public static boolean isFinal(XExecutableElement element) {
    if (element.isFinal()) {
      return true;
    }
    if (getProcessingEnv(element).getBackend() == XProcessingEnv.Backend.KSP) {
      if (toKS(element).getModifiers().contains(com.google.devtools.ksp.symbol.Modifier.FINAL)) {
        return true;
      }
    }
    return false;
  }

  public static ImmutableList<Modifier> getModifiers(XExecutableElement element) {
    ImmutableList.Builder<Modifier> builder = ImmutableList.builder();
    if (isFinal(element)) {
      builder.add(Modifier.FINAL);
    } else if (element.isAbstract()) {
      builder.add(Modifier.ABSTRACT);
    }
    if (element.isStatic()) {
      builder.add(Modifier.STATIC);
    }
    if (element.isPublic()) {
      builder.add(Modifier.PUBLIC);
    } else if (element.isPrivate()) {
      builder.add(Modifier.PRIVATE);
    } else if (element.isProtected()) {
      builder.add(Modifier.PROTECTED);
    }
    return builder.build();
  }

  private XElements() {}
}
