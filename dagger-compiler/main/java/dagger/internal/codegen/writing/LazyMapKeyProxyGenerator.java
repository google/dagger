/*
 * Copyright (C) 2024 The Dagger Authors.
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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static dagger.internal.codegen.binding.MapKeys.KEEP_FIELD_TYPE_FIELD;
import static dagger.internal.codegen.binding.MapKeys.LAZY_CLASS_KEY_NAME_FIELD;
import static dagger.internal.codegen.binding.MapKeys.lazyClassKeyProxyClassName;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import javax.inject.Inject;

/**
 * Generate a class containing fields that works with proguard rules to support @LazyClassKey
 * usages.
 */
public final class LazyMapKeyProxyGenerator extends SourceFileGenerator<XMethodElement> {

  @Inject
  LazyMapKeyProxyGenerator(XFiler filer, XProcessingEnv processingEnv) {
    super(filer, processingEnv);
  }

  @Override
  public XElement originatingElement(XMethodElement input) {
    return input;
  }

  @Override
  public ImmutableList<XTypeSpec> topLevelTypes(XMethodElement input) {
    return ImmutableList.of(lazyClassKeyProxyTypeSpec(input));
  }

  private XTypeSpec lazyClassKeyProxyTypeSpec(XMethodElement element) {
    return XTypeSpecs.classBuilder(lazyClassKeyProxyClassName(element))
        .addModifiers(PUBLIC, FINAL)
        .addAnnotation(XTypeNames.IDENTIFIER_NAME_STRING)
        .addProperties(lazyClassKeyFields(element))
        .build();
  }

  private static ImmutableList<XPropertySpec> lazyClassKeyFields(XMethodElement element) {
    XClassName lazyClassMapKeyClassName =
        element
            .getAnnotation(toJavaPoet(XTypeNames.LAZY_CLASS_KEY))
            .getAsType("value")
            .getTypeElement()
            .asClassName();
    // Generate a string referencing the map key class name, and dagger will apply
    // identifierrnamestring rule to it to make sure it is correctly obfuscated.
    XPropertySpec lazyClassKeyField =
        XPropertySpecs.builder(LAZY_CLASS_KEY_NAME_FIELD, XTypeName.STRING)
            // TODO(b/217435141): Leave the field as non-final. We will apply
            // @IdentifierNameString on the field, which doesn't work well with static final
            // fields.
            .addModifiers(STATIC, PUBLIC)
            .initializer("%S", lazyClassMapKeyClassName.getReflectionName())
            .build();
    // In proguard, we need to keep the classes referenced by @LazyClassKey, we do that by
    // generating a field referencing the type, and then applying @KeepFieldType to the
    // field. Here, we generate the field in the proxy class. For classes that are
    // accessible from the dagger component, we generate fields in LazyClassKeyProvider.
    // Note: the generated field should not be initialized to avoid class loading.
    XPropertySpec keepFieldTypeField =
        XPropertySpecs.builder(KEEP_FIELD_TYPE_FIELD, lazyClassMapKeyClassName)
            .addModifiers(STATIC)
            .addAnnotation(XTypeNames.KEEP_FIELD_TYPE)
            .build();
    return ImmutableList.of(keepFieldTypeField, lazyClassKeyField);
  }
}
