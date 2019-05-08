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

package dagger.android.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic.Kind;

/**
 * A descriptor of a generated {@link Module} and {@link dagger.Subcomponent} to be generated from a
 * {@link ContributesAndroidInjector} method.
 */
@AutoValue
abstract class AndroidInjectorDescriptor {
  /** The type to be injected; the return type of the {@link ContributesAndroidInjector} method. */
  abstract ClassName injectedType();

  /** Scopes to apply to the generated {@link dagger.Subcomponent}. */
  abstract ImmutableSet<AnnotationSpec> scopes();

  /** @see ContributesAndroidInjector#modules() */
  abstract ImmutableSet<ClassName> modules();

  /** The {@link Module} that contains the {@link ContributesAndroidInjector} method. */
  abstract ClassName enclosingModule();

  /** The method annotated with {@link ContributesAndroidInjector}. */
  abstract ExecutableElement method();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder injectedType(ClassName injectedType);

    abstract ImmutableSet.Builder<AnnotationSpec> scopesBuilder();

    abstract ImmutableSet.Builder<ClassName> modulesBuilder();

    abstract Builder enclosingModule(ClassName enclosingModule);

    abstract Builder method(ExecutableElement method);

    abstract AndroidInjectorDescriptor build();
  }

  static final class Validator {
    private final Messager messager;

    Validator(Messager messager) {
      this.messager = messager;
    }

    /**
     * Validates a {@link ContributesAndroidInjector} method, returning an {@link
     * AndroidInjectorDescriptor} if it is valid, or {@link Optional#empty()} otherwise.
     */
    Optional<AndroidInjectorDescriptor> createIfValid(ExecutableElement method) {
      ErrorReporter reporter = new ErrorReporter(method, messager);

      if (!method.getModifiers().contains(ABSTRACT)) {
        reporter.reportError("@ContributesAndroidInjector methods must be abstract");
      }

      if (!method.getParameters().isEmpty()) {
        reporter.reportError("@ContributesAndroidInjector methods cannot have parameters");
      }

      AndroidInjectorDescriptor.Builder builder =
          new AutoValue_AndroidInjectorDescriptor.Builder().method(method);
      TypeElement enclosingElement = MoreElements.asType(method.getEnclosingElement());
      if (!isAnnotationPresent(enclosingElement, Module.class)) {
        reporter.reportError("@ContributesAndroidInjector methods must be in a @Module");
      }
      builder.enclosingModule(ClassName.get(enclosingElement));

      TypeMirror injectedType = method.getReturnType();
      if (MoreTypes.asDeclared(injectedType).getTypeArguments().isEmpty()) {
        builder.injectedType(ClassName.get(MoreTypes.asTypeElement(injectedType)));
      } else {
        reporter.reportError(
            "@ContributesAndroidInjector methods cannot return parameterized types");
      }

      AnnotationMirror annotation =
          getAnnotationMirror(method, ContributesAndroidInjector.class).get();
      for (TypeMirror module :
          getAnnotationValue(annotation, "modules").accept(new AllTypesVisitor(), null)) {
        if (isAnnotationPresent(MoreTypes.asElement(module), Module.class)) {
          builder.modulesBuilder().add((ClassName) TypeName.get(module));
        } else {
          reporter.reportError(String.format("%s is not a @Module", module), annotation);
        }
      }

      for (AnnotationMirror scope : getAnnotatedAnnotations(method, Scope.class)) {
        builder.scopesBuilder().add(AnnotationSpec.get(scope));
      }

      for (AnnotationMirror qualifier : getAnnotatedAnnotations(method, Qualifier.class)) {
        reporter.reportError(
            "@ContributesAndroidInjector methods cannot have qualifiers", qualifier);
      }

      return reporter.hasError ? Optional.empty() : Optional.of(builder.build());
    }

    // TODO(ronshapiro): use ValidationReport once it is moved out of the compiler
    private static class ErrorReporter {
      private final Element subject;
      private final Messager messager;
      private boolean hasError;

      ErrorReporter(Element subject, Messager messager) {
        this.subject = subject;
        this.messager = messager;
      }

      void reportError(String error) {
        hasError = true;
        messager.printMessage(Kind.ERROR, error, subject);
      }

      void reportError(String error, AnnotationMirror annotation) {
        hasError = true;
        messager.printMessage(Kind.ERROR, error, subject, annotation);
      }
    }
  }

  private static final class AllTypesVisitor
      extends SimpleAnnotationValueVisitor8<ImmutableSet<TypeMirror>, Void> {
    @Override
    public ImmutableSet<TypeMirror> visitArray(List<? extends AnnotationValue> values, Void aVoid) {
      return ImmutableSet.copyOf(
          values.stream().flatMap(v -> v.accept(this, null).stream()).collect(toList()));
    }

    @Override
    public ImmutableSet<TypeMirror> visitType(TypeMirror a, Void aVoid) {
      return ImmutableSet.of(a);
    }

    @Override
    protected ImmutableSet<TypeMirror> defaultAction(Object o, Void aVoid) {
      throw new AssertionError(o);
    }
  }
}
