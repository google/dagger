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

import static dagger.model.BindingKind.MEMBERS_INJECTOR;

import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * A value object that represents a field in the generated Component class.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code Provider<String>}
 *   <li>{@code Producer<Widget>}
 *   <li>{@code Provider<Map<SomeMapKey, MapValue>>}.
 * </ul>
 */
@AutoValue
abstract class FrameworkField {

  /**
   * Creates a framework field.
   * 
   * @param frameworkClassName the name of the framework class (e.g., {@link javax.inject.Provider})
   * @param valueTypeName the name of the type parameter of the framework class (e.g., {@code Foo}
   *     for {@code Provider<Foo>}
   * @param fieldName the name of the field
   */
  static FrameworkField create(
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
  static FrameworkField forBinding(
      ContributionBinding binding, Optional<ClassName> frameworkClass) {
    return create(
        frameworkClass.orElse(
            ClassName.get(
                FrameworkType.forBindingType(binding.bindingType()).frameworkClass())),
        TypeName.get(fieldValueType(binding)),
        frameworkFieldName(binding));
  }

  private static TypeMirror fieldValueType(ContributionBinding binding) {
    return binding.contributionType().isMultibinding()
        ? binding.contributedType()
        : binding.key().type();
  }

  private static String frameworkFieldName(ContributionBinding binding) {
    if (binding.bindingElement().isPresent()) {
      String name = BINDING_ELEMENT_NAME.visit(binding.bindingElement().get(), binding);
      return binding.kind().equals(MEMBERS_INJECTOR) ? name + "MembersInjector" : name;
    }
    return KeyVariableNamer.name(binding.key());
  }

  private static final ElementVisitor<String, Binding> BINDING_ELEMENT_NAME =
      new ElementKindVisitor8<String, Binding>() {

        @Override
        protected String defaultAction(Element e, Binding p) {
          throw new IllegalArgumentException("Unexpected binding " + p);
        }

        @Override
        public String visitExecutableAsConstructor(ExecutableElement e, Binding p) {
          return visit(e.getEnclosingElement(), p);
        }

        @Override
        public String visitExecutableAsMethod(ExecutableElement e, Binding p) {
          return e.getSimpleName().toString();
        }

        @Override
        public String visitType(TypeElement e, Binding p) {
          return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, e.getSimpleName().toString());
        }

        @Override
        public String visitVariableAsParameter(VariableElement e, Binding p) {
          return e.getSimpleName().toString();
        }
      };

  abstract ParameterizedTypeName type();
  abstract String name();
}
