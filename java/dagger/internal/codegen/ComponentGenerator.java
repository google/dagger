/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static dagger.internal.codegen.SourceFiles.classFileName;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final DaggerTypes types;
  private final Elements elements;
  private final KeyFactory keyFactory;
  private final CompilerOptions compilerOptions;

  ComponentGenerator(
      Filer filer,
      Elements elements,
      DaggerTypes types,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    return componentName(input.componentType());
  }

  static ClassName componentName(TypeElement componentDefinitionType) {
    ClassName componentName = ClassName.get(componentDefinitionType);
    return ClassName.get(componentName.packageName(), "Dagger" + classFileName(componentName));
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentType());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName componentName, BindingGraph input) {
    return Optional.of(
        ComponentWriter.write(types, elements, keyFactory, compilerOptions, componentName, input));
  }
}
