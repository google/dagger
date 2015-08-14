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
package dagger2.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import dagger2.internal.codegen.writer.ClassName;
import dagger2.internal.codegen.writer.ParameterizedTypeName;
import dagger2.internal.codegen.writer.TypeName;
import dagger2.internal.codegen.writer.TypeNames;
import javax.lang.model.type.TypeMirror;

/**
 * A value object that represents a field used by Dagger-generated code.
 *
 * @author Jesse Beder
 * @since 2.0
 */
@AutoValue
abstract class FrameworkField {
  // TODO(gak): reexamine the this class and how consistently we're using it and its creation
  // methods

  static FrameworkField createWithTypeFromKey(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    String suffix = frameworkClass.getSimpleName();
    ParameterizedTypeName frameworkType = ParameterizedTypeName.create(
        ClassName.fromClass(frameworkClass),
        TypeNames.forTypeMirror(bindingKey.key().type()));
    return new AutoValue_FrameworkField(frameworkClass, frameworkType, bindingKey,
        name.endsWith(suffix) ? name : name + suffix);
  }

  static FrameworkField createForMapBindingContribution(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    TypeMirror mapValueType =
        MoreTypes.asDeclared(bindingKey.key().type()).getTypeArguments().get(1);
    return new AutoValue_FrameworkField(frameworkClass,
        TypeNames.forTypeMirror(mapValueType),
        bindingKey,
        name);
  }

  abstract Class<?> frameworkClass();
  abstract TypeName frameworkType();
  abstract BindingKey bindingKey();
  abstract String name();
}
