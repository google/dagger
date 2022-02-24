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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.spi.model.BindingKind.MEMBERS_INJECTOR;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Optional;

/**
 * A value object that represents a field in the generated Component class.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code Provider<String>}
 *   <li>{@code Producer<Widget>}
 *   <li>{@code Provider<Map<SomeMapKey, MapValue>>}.
 * </ul>
 */
@AutoValue
public abstract class FrameworkField {

  /**
   * Creates a framework field.
   *
   * @param frameworkClassName the name of the framework class (e.g., {@link javax.inject.Provider})
   * @param valueTypeName the name of the type parameter of the framework class (e.g., {@code Foo}
   *     for {@code Provider<Foo>}
   * @param fieldName the name of the field
   */
  public static FrameworkField create(
      ClassName frameworkClassName, TypeName valueTypeName, String fieldName) {
    String suffix = frameworkClassName.simpleName();
    return new AutoValue_FrameworkField(
        ParameterizedTypeName.get(frameworkClassName, valueTypeName),
        fieldName.endsWith(suffix) ? fieldName : fieldName + suffix);
  }

  /**
   * A framework field for a {@link ContributionBinding}.
   *
   * @param frameworkClass if present, the field will use this framework class instead of the normal
   *     one for the binding's type.
   */
  public static FrameworkField forBinding(
      ContributionBinding binding, Optional<ClassName> frameworkClassName) {
    return create(
        frameworkClassName.orElse(binding.frameworkType().frameworkClassName()),
        fieldValueType(binding).getTypeName(),
        frameworkFieldName(binding));
  }

  private static XType fieldValueType(ContributionBinding binding) {
    return binding.contributionType().isMultibinding()
        ? binding.contributedType()
        : binding.key().type().xprocessing();
  }

  private static String frameworkFieldName(ContributionBinding binding) {
    if (binding.bindingElement().isPresent()) {
      String name = bindingElementName(binding.bindingElement().get());
      return binding.kind().equals(MEMBERS_INJECTOR) ? name + "MembersInjector" : name;
    }
    return KeyVariableNamer.name(binding.key());
  }

  private static String bindingElementName(XElement bindingElement) {
    if (isConstructor(bindingElement)) {
      return bindingElementName(bindingElement.getEnclosingElement());
    } else if (isMethod(bindingElement)) {
      return getSimpleName(bindingElement);
    } else if (isTypeElement(bindingElement)) {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, getSimpleName(bindingElement));
    } else if (isMethodParameter(bindingElement)) {
      return getSimpleName(bindingElement);
    } else {
      throw new IllegalArgumentException("Unexpected binding " + bindingElement);
    }
  }

  public abstract ParameterizedTypeName type();

  public abstract String name();
}
