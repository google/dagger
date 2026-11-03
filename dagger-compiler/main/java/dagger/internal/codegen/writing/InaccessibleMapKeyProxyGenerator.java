/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.base.MapKeyAccessibility.isMapKeyPubliclyAccessible;
import static dagger.internal.codegen.binding.MapKeys.directMapKeyExpression;
import static dagger.internal.codegen.binding.MapKeys.mapKeyProxyClassName;
import static dagger.internal.codegen.binding.MapKeys.mapKeyType;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeSpecs.objectBuilder;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room3.compiler.codegen.XFunSpec;
import androidx.room3.compiler.codegen.XTypeSpec;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XFiler;
import androidx.room3.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Generates a class that exposes a non-{@code public} {@link
 * ContributionBinding#mapKeyAnnotation()} @MapKey} annotation.
 */
public final class InaccessibleMapKeyProxyGenerator
    extends SourceFileGenerator<ContributionBinding> {
  private final XProcessingEnv processingEnv;

  @Inject
  InaccessibleMapKeyProxyGenerator(
      XProcessingEnv processingEnv,
      XFiler filer) {
    super(filer, processingEnv);
    this.processingEnv = processingEnv;
  }

  @Override
  public XElement originatingElement(ContributionBinding binding) {
    // a map key is only ever present on bindings that have a binding element
    return binding.bindingElement().get();
  }

  @Override
  public ImmutableList<XTypeSpec> topLevelTypes(ContributionBinding binding) {
    return mapKeyFactoryMethod(binding)
        .map(
            method ->
                ImmutableList.of(
                    objectBuilder(mapKeyProxyClassName(binding))
                        .addModifiers(PUBLIC)
                        .addFunction(method)
                        .build()))
        .orElse(ImmutableList.of());
  }

  private Optional<XFunSpec> mapKeyFactoryMethod(ContributionBinding binding) {
    return binding
        .mapKey()
        // @LazyClassKey usages are handled by LazyMapKeyProxyGenerator instead.
        .filter(mapKey -> !mapKey.getTypeElement().asClassName().equals(XTypeNames.LAZY_CLASS_KEY))
        .filter(mapKey -> !isMapKeyPubliclyAccessible(mapKey))
        .map(
            mapKey ->
                methodBuilder("create")
                    .addModifiers(PUBLIC, STATIC)
                    .returns(mapKeyType(mapKey).asTypeName())
                    .addStatement("return %L", directMapKeyExpression(mapKey, processingEnv))
                    .build());
  }
}
