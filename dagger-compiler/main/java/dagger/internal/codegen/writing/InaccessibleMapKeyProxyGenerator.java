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

import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XTypeSpecs.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.MapKeys;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
  InaccessibleMapKeyProxyGenerator(XProcessingEnv processingEnv, XFiler filer) {
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
    return MapKeys.mapKeyFactoryMethod(binding, processingEnv)
        .map(
            method -> {
              XTypeSpecs.Builder builder =
                  classBuilder(MapKeys.mapKeyProxyClassName(binding))
                      .addModifiers(PUBLIC, FINAL)
                      .addFunction(constructorBuilder().addModifiers(PRIVATE).build())
                      .addFunction(method);
              lazyClassKeyField(binding).ifPresent(builder::addProperty);
              return ImmutableList.of(builder.build());
            })
        .orElse(ImmutableList.<XTypeSpec>of());
  }

  private Optional<XPropertySpec> lazyClassKeyField(ContributionBinding binding) {
    // In proguard, we need to keep the classes referenced by @LazyClassKey, we do that by
    // generating a field referencing the type, and then applying @KeepFieldType to the
    // field. Here, we generate the field in the proxy class. For classes that are
    // accessible from the dagger component, we generate fields in LazyClassKeyProvider.
    // Note: the generated field should not be initialized to avoid class loading.
    return binding
        .mapKey()
        .filter(mapKey -> mapKey.getTypeElement().asClassName().equals(XTypeNames.LAZY_CLASS_KEY))
        .map(
            mapKey ->
                XPropertySpecs.builder("className", mapKey.getAsType("value").asTypeName())
                    .addAnnotation(XTypeNames.KEEP_FIELD_TYPE)
                    .build());
  }
}
