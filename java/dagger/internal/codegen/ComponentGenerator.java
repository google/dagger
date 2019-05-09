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

import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.SourceFiles.classFileName;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final ComponentImplementationFactory componentImplementationFactory;

  @Inject
  ComponentGenerator(
      Filer filer,
      DaggerElements elements,
      SourceVersion sourceVersion,
      ComponentImplementationFactory componentImplementationFactory) {
    super(filer, elements, sourceVersion);
    this.componentImplementationFactory = componentImplementationFactory;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    return componentName(input.componentTypeElement());
  }

  static ClassName componentName(TypeElement componentDefinitionType) {
    ClassName componentName = ClassName.get(componentDefinitionType);
    return ClassName.get(componentName.packageName(), "Dagger" + classFileName(componentName));
  }

  @Override
  Element originatingElement(BindingGraph input) {
    return input.componentTypeElement();
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName componentName, BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        componentImplementationFactory.createComponentImplementation(bindingGraph);
    verify(componentImplementation.name().equals(componentName));
    return Optional.of(componentImplementation.generate());
  }
}
