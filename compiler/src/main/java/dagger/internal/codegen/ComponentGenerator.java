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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.JavaWriter;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends JavaWriterSourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final Diagnostic.Kind nullableValidationType;

  ComponentGenerator(
      Filer filer,
      Elements elements,
      Types types,
      Key.Factory keyFactory,
      Diagnostic.Kind nullableValidationType) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.nullableValidationType = nullableValidationType;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDescriptor().componentDefinitionType());
    String componentName = "Dagger" + componentDefinitionClassName.classFileName('_');
    return componentDefinitionClassName.topLevelClassName().peerNamed(componentName);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(BindingGraph input) {
    return ImmutableSet.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName componentName, BindingGraph input) {
    return new ComponentWriter(
            types, elements, keyFactory, nullableValidationType, componentName, input)
        .write();
  }
}
