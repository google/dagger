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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.ErrorMessages.ComponentBuilderMessages;
import dagger.internal.codegen.ValidationReport.Builder;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

/**
 * Validates {@link dagger.Component.Builder} annotations.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class BuilderValidator {

  private final DaggerElements elements;
  private final Types types;

  BuilderValidator(DaggerElements elements, Types types) {
    this.elements = elements;
    this.types = types;
  }

  public ValidationReport<TypeElement> validate(TypeElement subject) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(subject);

    ComponentDescriptor.Kind componentKind =
        ComponentDescriptor.Kind.forAnnotatedBuilderElement(subject).get();

    Element componentElement = subject.getEnclosingElement();
    ErrorMessages.ComponentBuilderMessages msgs = ErrorMessages.builderMsgsFor(componentKind);
    Class<? extends Annotation> componentAnnotation = componentKind.annotationType();
    Class<? extends Annotation> builderAnnotation = componentKind.builderAnnotationType();
    checkArgument(subject.getAnnotation(builderAnnotation) != null);

    if (!isAnnotationPresent(componentElement, componentAnnotation)) {
      builder.addError(msgs.mustBeInComponent(), subject);
    }

    switch (subject.getKind()) {
      case CLASS:
        List<? extends Element> allElements = subject.getEnclosedElements();
        List<ExecutableElement> cxtors = ElementFilter.constructorsIn(allElements);
        if (cxtors.size() != 1 || getOnlyElement(cxtors).getParameters().size() != 0) {
          builder.addError(msgs.cxtorOnlyOneAndNoArgs(), subject);
        }
        break;
      case INTERFACE:
        break;
      default:
        // If not the correct type, exit early since the rest of the messages will be bogus.
        builder.addError(msgs.mustBeClassOrInterface(), subject);
        return builder.build();
    }

    if (!subject.getTypeParameters().isEmpty()) {
      builder.addError(msgs.generics(), subject);
    }

    Set<Modifier> modifiers = subject.getModifiers();
    if (modifiers.contains(PRIVATE)) {
      builder.addError(msgs.isPrivate(), subject);
    }
    if (!modifiers.contains(STATIC)) {
      builder.addError(msgs.mustBeStatic(), subject);
    }
    // Note: Must be abstract, so no need to check for final.
    if (!modifiers.contains(ABSTRACT)) {
      builder.addError(msgs.mustBeAbstract(), subject);
    }

    ExecutableElement buildMethod = null;
    for (ExecutableElement method : elements.getUnimplementedMethods(subject)) {
      ExecutableType resolvedMethodType =
          MoreTypes.asExecutable(types.asMemberOf(MoreTypes.asDeclared(subject.asType()), method));
      TypeMirror returnType = resolvedMethodType.getReturnType();
      if (method.getParameters().size() == 0) {
        // If this is potentially a build() method, validate it returns the correct type.
        if (types.isSubtype(componentElement.asType(), returnType)) {
          validateBuildMethodReturnType(
              builder,
              // since types.isSubtype() passed, componentElement cannot be a PackageElement
              MoreElements.asType(componentElement),
              msgs,
              method,
              returnType);
          if (buildMethod != null) {
            // If we found more than one build-like method, fail.
            error(
                builder,
                method,
                msgs.twoBuildMethods(),
                msgs.inheritedTwoBuildMethods(),
                buildMethod);
          }
        } else {
          error(
              builder,
              method,
              msgs.buildMustReturnComponentType(),
              msgs.inheritedBuildMustReturnComponentType());
        }
        // We set the buildMethod regardless of the return type to reduce error spam.
        buildMethod = method;
      } else if (method.getParameters().size() > 1) {
        // If this is a setter, make sure it has one arg.
        error(builder, method, msgs.methodsMustTakeOneArg(), msgs.inheritedMethodsMustTakeOneArg());
      } else if (returnType.getKind() != TypeKind.VOID
          && !types.isSubtype(subject.asType(), returnType)) {
        // If this correctly had one arg, make sure the return types are valid.
        error(
            builder,
            method,
            msgs.methodsMustReturnVoidOrBuilder(),
            msgs.inheritedMethodsMustReturnVoidOrBuilder());
      } else if (!method.getTypeParameters().isEmpty()) {
        error(
            builder,
            method,
            msgs.methodsMayNotHaveTypeParameters(),
            msgs.inheritedMethodsMayNotHaveTypeParameters());
      }
    }

    if (buildMethod == null) {
      builder.addError(msgs.missingBuildMethod(), subject);
    }

    // Note: there's more validation in BindingGraphValidator:
    // - to make sure the setter methods mirror the deps
    // - to make sure each type or key is set by only one method

    return builder.build();
  }

  private void validateBuildMethodReturnType(
      ValidationReport.Builder<TypeElement> builder,
      TypeElement componentElement,
      ComponentBuilderMessages msgs,
      ExecutableElement method,
      TypeMirror returnType) {
    if (types.isSameType(componentElement.asType(), returnType)) {
      return;
    }
    ImmutableSet<ExecutableElement> methodsOnlyInComponent =
        methodsOnlyInComponent(componentElement);
    if (!methodsOnlyInComponent.isEmpty()) {
      builder.addWarning(
          msgs.buildMethodReturnsSupertypeWithMissingMethods(
              componentElement, builder.getSubject(), returnType, method, methodsOnlyInComponent),
          method);
    }
  }

  /**
   * Generates one of two error messages. If the method is enclosed in the subject, we target the
   * error to the method itself. Otherwise we target the error to the subject and list the method as
   * an argumnent. (Otherwise we have no way of knowing if the method is being compiled in this pass
   * too, so javac might not be able to pinpoint it's line of code.)
   */
  /*
   * For Component.Builder, the prototypical example would be if someone had:
   *    libfoo: interface SharedBuilder { void badSetter(A a, B b); }
   *    libbar: BarComponent { BarBuilder extends SharedBuilder } }
   * ... the compiler only validates BarBuilder when compiling libbar, but it fails because
   * of libfoo's SharedBuilder (which could have been compiled in a previous pass).
   * So we can't point to SharedBuilder#badSetter as the subject of the BarBuilder validation
   * failure.
   *
   * This check is a little more strict than necessary -- ideally we'd check if method's enclosing
   * class was included in this compile run.  But that's hard, and this is close enough.
   */
  private static void error(
      ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method,
      String enclosedError,
      String inheritedError,
      Object... extraArgs) {
    if (method.getEnclosingElement().equals(builder.getSubject())) {
      builder.addError(String.format(enclosedError, extraArgs), method);
    } else {
      builder.addError(String.format(inheritedError, append(extraArgs, method)));
    }
  }

  /** @see #error(Builder, ExecutableElement, String, String, Object...) */
  private static void warning(
      ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method,
      String enclosedWarning,
      String inheritedWarning,
      Object... extraArgs) {
    if (method.getEnclosingElement().equals(builder.getSubject())) {
      builder.addWarning(String.format(enclosedWarning, extraArgs), method);
    } else {
      builder.addWarning(String.format(inheritedWarning, append(extraArgs, method)), method);
    }
  }

  private static Object[] append(Object[] initial, Object additional) {
    Object[] newArray = Arrays.copyOf(initial, initial.length + 1);
    newArray[initial.length] = additional;
    return newArray;
  }

  /**
   * Returns all methods defind in {@code componentType} which are not inherited from a supertype.
   */
  private ImmutableSet<ExecutableElement> methodsOnlyInComponent(TypeElement componentType) {
    // TODO(ronshapiro): Ideally this shouldn't return methods which are redeclared from a
    // supertype, but do not change the return type. We don't have a good/simple way of checking
    // that, and it doesn't seem likely, so the warning won't be too bad.
    return ImmutableSet.copyOf(methodsIn(componentType.getEnclosedElements()));
  }
}
