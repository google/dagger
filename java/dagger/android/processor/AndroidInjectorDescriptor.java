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

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Optional;
import javax.tools.Diagnostic.Kind;

/**
 * A descriptor of a generated {@link dagger.Module} and {@link dagger.Subcomponent} to be generated
 * from a {@code ContributesAndroidInjector} method.
 */
@AutoValue
abstract class AndroidInjectorDescriptor {
  /** The type to be injected; the return type of the {@code ContributesAndroidInjector} method. */
  abstract ClassName injectedType();

  /** Scopes to apply to the generated {@link dagger.Subcomponent}. */
  abstract ImmutableSet<AnnotationSpec> scopes();

  /** See {@code ContributesAndroidInjector#modules()} */
  abstract ImmutableSet<ClassName> modules();

  /** The {@link dagger.Module} that contains the {@code ContributesAndroidInjector} method. */
  abstract ClassName enclosingModule();

  /** The method annotated with {@code ContributesAndroidInjector}. */
  abstract XExecutableElement method();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder injectedType(ClassName injectedType);

    abstract ImmutableSet.Builder<AnnotationSpec> scopesBuilder();

    abstract ImmutableSet.Builder<ClassName> modulesBuilder();

    abstract Builder enclosingModule(ClassName enclosingModule);

    abstract Builder method(XExecutableElement method);

    abstract AndroidInjectorDescriptor build();
  }

  static final class Validator {
    private final XMessager messager;

    Validator(XMessager messager) {
      this.messager = messager;
    }

    /**
     * Validates a {@code ContributesAndroidInjector} method, returning an {@link
     * AndroidInjectorDescriptor} if it is valid, or {@link Optional#empty()} otherwise.
     */
    Optional<AndroidInjectorDescriptor> createIfValid(XMethodElement method) {
      ErrorReporter reporter = new ErrorReporter(method, messager);

      if (!method.isAbstract()) {
        reporter.reportError("@ContributesAndroidInjector methods must be abstract");
      }

      if (!method.getParameters().isEmpty()) {
        reporter.reportError("@ContributesAndroidInjector methods cannot have parameters");
      }

      AndroidInjectorDescriptor.Builder builder =
          new AutoValue_AndroidInjectorDescriptor.Builder().method(method);
      XTypeElement enclosingElement = XElements.asTypeElement(method.getEnclosingElement());
      if (!enclosingElement.hasAnnotation(TypeNames.MODULE)) {
        reporter.reportError("@ContributesAndroidInjector methods must be in a @Module");
      }
      builder.enclosingModule(enclosingElement.getClassName());

      XType injectedType = method.getReturnType();
      if (injectedType.getTypeArguments().isEmpty()) {
        builder.injectedType(injectedType.getTypeElement().getClassName());
      } else {
        reporter.reportError(
            "@ContributesAndroidInjector methods cannot return parameterized types");
      }

      XAnnotation annotation = method.getAnnotation(TypeNames.CONTRIBUTES_ANDROID_INJECTOR);
      for (XType module : getTypeList(annotation.getAnnotationValue("modules"))) {
        if (module.getTypeElement().hasAnnotation(TypeNames.MODULE)) {
          builder.modulesBuilder().add((ClassName) module.getTypeName());
        } else {
          reporter.reportError(String.format("%s is not a @Module", module), annotation);
        }
      }

      for (XAnnotation scope :
          Sets.union(
              method.getAnnotationsAnnotatedWith(TypeNames.SCOPE),
              method.getAnnotationsAnnotatedWith(TypeNames.SCOPE_JAVAX))) {
        builder.scopesBuilder().add(JavaPoetExtKt.toAnnotationSpec(scope));
      }

      for (XAnnotation qualifier :
          Sets.union(
              method.getAnnotationsAnnotatedWith(TypeNames.QUALIFIER),
              method.getAnnotationsAnnotatedWith(TypeNames.QUALIFIER_JAVAX))) {
        reporter.reportError(
            "@ContributesAndroidInjector methods cannot have qualifiers", qualifier);
      }

      return reporter.hasError ? Optional.empty() : Optional.of(builder.build());
    }

    private static ImmutableList<XType> getTypeList(XAnnotationValue annotationValue) {
      if (annotationValue.hasTypeListValue()) {
        return ImmutableList.copyOf(annotationValue.asTypeList());
      }
      if (annotationValue.hasTypeValue()) {
        return ImmutableList.of(annotationValue.asType());
      }
      throw new IllegalArgumentException("Does not have type list");
    }

    // TODO(ronshapiro): use ValidationReport once it is moved out of the compiler
    private static class ErrorReporter {
      private final XElement subject;
      private final XMessager messager;
      private boolean hasError;

      ErrorReporter(XElement subject, XMessager messager) {
        this.subject = subject;
        this.messager = messager;
      }

      void reportError(String error) {
        hasError = true;
        messager.printMessage(Kind.ERROR, error, subject);
      }

      void reportError(String error, XAnnotation annotation) {
        hasError = true;
        messager.printMessage(Kind.ERROR, error, subject, annotation);
      }
    }
  }
}
