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

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * A value object that represents a field used by Dagger-generated code.
 *
 * @author Jesse Beder
 * @since 2.0
 */
@AutoValue
// TODO(gak): Reexamine the this class and how consistently we're using it and its creation methods.
abstract class FrameworkField {
  static FrameworkField createWithTypeFromKey(Class<?> frameworkClass, Key key, String name) {
    String suffix = frameworkClass.getSimpleName();
    ParameterizedTypeName frameworkType =
        ParameterizedTypeName.get(ClassName.get(frameworkClass), TypeName.get(key.type()));
    return new AutoValue_FrameworkField(
        frameworkType, name.endsWith(suffix) ? name : name + suffix);
  }

  private static FrameworkField createForMapBindingContribution(Key key, String name) {
    TypeMirror type = MapType.from(key.type()).valueType();
    String suffix = MoreTypes.asDeclared(type).asElement().getSimpleName().toString();
    return new AutoValue_FrameworkField(
        (ParameterizedTypeName) TypeName.get(type),
        name.endsWith(suffix) ? name : name + suffix);
  }

  static FrameworkField createForResolvedBindings(
      ResolvedBindings resolvedBindings, Optional<BindingType> bindingType) {
    if (resolvedBindings.isMultibindingContribution()
        && resolvedBindings.contributionType().equals(ContributionType.MAP)) {
      return createForMapBindingContribution(
          resolvedBindings.key(), frameworkFieldName(resolvedBindings));
    } else {
      return createWithTypeFromKey(
          bindingType.isPresent()
              ? bindingType.get().frameworkClass()
              : resolvedBindings.frameworkClass(),
          resolvedBindings.key(),
          frameworkFieldName(resolvedBindings));
    }
  }

  private static String frameworkFieldName(ResolvedBindings resolvedBindings) {
    if (resolvedBindings.bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)) {
      ContributionBinding binding = resolvedBindings.contributionBinding();
      if (!binding.isSyntheticBinding()) {
        return BINDING_ELEMENT_NAME.visit(binding.bindingElement(), binding);
      }
    }
    return KeyVariableNamer.INSTANCE.apply(resolvedBindings.key());
  }

  private static final ElementVisitor<String, Binding> BINDING_ELEMENT_NAME =
      new ElementKindVisitor6<String, Binding>() {

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
      };

  abstract ParameterizedTypeName frameworkType();
  abstract String name();
}
