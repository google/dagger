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

package dagger.internal.codegen;

import static dagger.internal.codegen.ValidationType.NONE;
import static javax.tools.Diagnostic.Kind.NOTE;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Context;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * A module that provides a {@link BindingGraphFactory} and {@link ComponentDescriptorFactory} for
 * use in {@code javac} plugins. Requires a binding for the {@code javac} {@link Context}.
 */
@Module(includes = InjectBindingRegistryModule.class)
abstract class JavacPluginModule {
  @Provides
  static CompilerOptions compilerOptions() {
    return new CompilerOptions() {
      @Override
      boolean usesProducers() {
        return true;
      }

      @Override
      boolean fastInit() {
        return false;
      }

      @Override
      boolean formatGeneratedSource() {
        return false;
      }

      @Override
      boolean writeProducerNameInToken() {
        return true;
      }

      @Override
      Diagnostic.Kind nullableValidationKind() {
        return NOTE;
      }

      @Override
      Diagnostic.Kind privateMemberValidationKind() {
        return NOTE;
      }

      @Override
      Diagnostic.Kind staticMemberValidationKind() {
        return NOTE;
      }

      @Override
      boolean ignorePrivateAndStaticInjectionForComponent() {
        return false;
      }

      @Override
      ValidationType scopeCycleValidationType() {
        return NONE;
      }

      @Override
      boolean warnIfInjectionFactoryNotGeneratedUpstream() {
        return false;
      }

      @Override
      boolean headerCompilation() {
        return false;
      }

      @Override
      boolean aheadOfTimeSubcomponents() {
        return false;
      }

      @Override
      boolean forceUseSerializedComponentImplementations() {
        return false;
      }

      @Override
      boolean emitModifiableMetadataAnnotations() {
        return false;
      }

      @Override
      boolean useGradleIncrementalProcessing() {
        return false;
      }

      @Override
      ValidationType fullBindingGraphValidationType(TypeElement element) {
        return NONE;
      }

      @Override
      Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
        return NOTE;
      }

      @Override
      ValidationType explicitBindingConflictsWithInjectValidationType() {
        return NONE;
      }
    };
  }

  @Binds
  abstract Messager messager(NullMessager nullMessager);

  static final class NullMessager implements Messager {

    @Inject
    NullMessager() {}

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence) {}

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence, Element element) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror,
        AnnotationValue annotationValue) {}
  }

  @Provides
  static DaggerElements daggerElements(Context javaContext) {
    return new DaggerElements(
        JavacElements.instance(javaContext), JavacTypes.instance(javaContext));
  }

  @Provides
  static DaggerTypes daggerTypes(Context javaContext, DaggerElements elements) {
    return new DaggerTypes(JavacTypes.instance(javaContext), elements);
  }

  @Binds abstract Types types(DaggerTypes daggerTypes);

  private JavacPluginModule() {}
}
