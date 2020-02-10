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

package dagger.internal.codegen.javac;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Context;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.ComponentDescriptorFactory;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.JavacPluginCompilerOptions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * A module that provides a {@link BindingGraphFactory} and {@link ComponentDescriptorFactory} for
 * use in {@code javac} plugins. Requires a binding for the {@code javac} {@link Context}.
 */
@Module
public abstract class JavacPluginModule {
  @Binds
  abstract CompilerOptions compilerOptions(JavacPluginCompilerOptions compilerOptions);

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
