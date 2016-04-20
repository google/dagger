/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final CompilerOptions compilerOptions;

  ComponentGenerator(
      Filer filer,
      Elements elements,
      Types types,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.get(input.componentDescriptor().componentDefinitionType());
    String componentName =
        "Dagger" + Joiner.on('_').join(componentDefinitionClassName.simpleNames());
    return componentDefinitionClassName.topLevelClassName().peerClass(componentName);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName componentName, BindingGraph input) {
    return Optional.of(
        new ComponentWriter(types, elements, keyFactory, compilerOptions, componentName, input)
            .write());
  }
}
