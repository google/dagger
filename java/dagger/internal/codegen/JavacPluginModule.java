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

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Context;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.ProcessingEnvironmentModule.ElementsModule;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * A module that provides a {@link BindingGraphFactory} and {@link ComponentDescriptor.Factory} for
 * use in {@code javac} plugins. Requires a binding for the {@code javac} {@link Context}.
 */
@Module(includes = {InjectBindingRegistryModule.class, ElementsModule.class})
abstract class JavacPluginModule {
  @Provides
  static CompilerOptions compilerOptions() {
    return CompilerOptions.builder()
        .usesProducers(true)
        .writeProducerNameInToken(true)
        .nullableValidationKind(Diagnostic.Kind.NOTE)
        .privateMemberValidationKind(Diagnostic.Kind.NOTE)
        .staticMemberValidationKind(Diagnostic.Kind.NOTE)
        .ignorePrivateAndStaticInjectionForComponent(false)
        .scopeCycleValidationType(ValidationType.NONE)
        .warnIfInjectionFactoryNotGeneratedUpstream(false)
        .fastInit(false)
        .experimentalAndroidMode2(false)
        .aheadOfTimeSubcomponents(false)
        .moduleBindingValidationType(ValidationType.NONE)
        .moduleHasDifferentScopesDiagnosticKind(Diagnostic.Kind.NOTE)
        .build()
        .validate();
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
