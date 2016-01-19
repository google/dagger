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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Validates {@link dagger.Component.Builder} annotations.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class BuilderValidator {
  private final Elements elements;
  private final Types types;

  BuilderValidator(Elements elements, Types types) {
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
    Multimap<Equivalence.Wrapper<TypeMirror>, ExecutableElement> methodsPerParam =
        LinkedHashMultimap.create();
    for (ExecutableElement method : Util.getUnimplementedMethods(elements, subject)) {
      ExecutableType resolvedMethodType =
          MoreTypes.asExecutable(types.asMemberOf(MoreTypes.asDeclared(subject.asType()), method));
      TypeMirror returnType = resolvedMethodType.getReturnType();
      if (method.getParameters().size() == 0) {
        // If this is potentially a build() method, validate it returns the correct type.
        if (types.isSameType(returnType, componentElement.asType())) {
          if (buildMethod != null) {
            // If we found more than one build-like method, fail.
            error(builder, method, msgs.twoBuildMethods(), msgs.inheritedTwoBuildMethods(),
                buildMethod);
          }
        } else {
          error(builder, method, msgs.buildMustReturnComponentType(),
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
        error(builder, method, msgs.methodsMustReturnVoidOrBuilder(),
            msgs.inheritedMethodsMustReturnVoidOrBuilder());
      } else {
        // If the return types are valid, record the method.
        methodsPerParam.put(
            MoreTypes.equivalence().<TypeMirror>wrap(
                Iterables.getOnlyElement(resolvedMethodType.getParameterTypes())),
            method);
      }

      if (!method.getTypeParameters().isEmpty()) {
        error(builder, method, msgs.methodsMayNotHaveTypeParameters(),
            msgs.inheritedMethodsMayNotHaveTypeParameters());
      }
    }

    if (buildMethod == null) {
      builder.addError(msgs.missingBuildMethod(), subject);
    }

    // Go back through each recorded method per param type.  If we had more than one method
    // for a given param, fail.
    for (Map.Entry<Equivalence.Wrapper<TypeMirror>, Collection<ExecutableElement>> entry :
        methodsPerParam.asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        TypeMirror type = entry.getKey().get();
        builder.addError(String.format(msgs.manyMethodsForType(), type, entry.getValue()), subject);
      }
    }

    // Note: there's more validation in BindingGraphValidator,
    // specifically to make sure the setter methods mirror the deps.

    return builder.build();
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
  private void error(
      ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method,
      String enclosedError,
      String inheritedError,
      Object... extraArgs) {
    if (method.getEnclosingElement().equals(builder.getSubject())) {
      builder.addError(String.format(enclosedError, extraArgs), method);
    } else {
      Object[] newArgs = new Object[extraArgs.length + 1];
      newArgs[0] = method;
      System.arraycopy(extraArgs, 0, newArgs, 1, extraArgs.length);
      builder.addError(String.format(inheritedError, newArgs), builder.getSubject());
    }
  }
}
