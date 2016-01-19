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

import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeNames;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

import static com.google.common.collect.Iterables.getOnlyElement;

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
        ParameterizedTypeName.create(
            ClassName.fromClass(frameworkClass), TypeNames.forTypeMirror(key.type()));
    com.squareup.javapoet.ParameterizedTypeName javapoetFrameworkType =
        com.squareup.javapoet.ParameterizedTypeName.get(
            com.squareup.javapoet.ClassName.get(frameworkClass),
            com.squareup.javapoet.TypeName.get(key.type()));
    return new AutoValue_FrameworkField(
        javapoetFrameworkType, frameworkType, name.endsWith(suffix) ? name : name + suffix);
  }

  private static FrameworkField createForMapBindingContribution(Key key, String name) {
    TypeMirror type = MapType.from(key.type()).valueType();
    return new AutoValue_FrameworkField(
        (com.squareup.javapoet.ParameterizedTypeName) com.squareup.javapoet.TypeName.get(type),
        (ParameterizedTypeName) TypeNames.forTypeMirror(type),
        name);
  }

  static FrameworkField createForSyntheticContributionBinding(
      int contributionNumber, ContributionBinding contributionBinding) {
    switch (contributionBinding.contributionType()) {
      case MAP:
        return createForMapBindingContribution(
            contributionBinding.key(),
            KeyVariableNamer.INSTANCE.apply(contributionBinding.key())
                + "Contribution"
                + contributionNumber);

      case SET:
      case UNIQUE:
        return createWithTypeFromKey(
            contributionBinding.frameworkClass(),
            contributionBinding.key(),
            KeyVariableNamer.INSTANCE.apply(contributionBinding.key())
                + "Contribution"
                + contributionNumber);
      default:
        throw new AssertionError();
    }
  }

  static FrameworkField createForResolvedBindings(ResolvedBindings resolvedBindings) {
    return createWithTypeFromKey(
        resolvedBindings.frameworkClass(),
        resolvedBindings.bindingKey().key(),
        frameworkFieldName(resolvedBindings));
  }

  private static String frameworkFieldName(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)
        && resolvedBindings.contributionType().equals(ContributionType.UNIQUE)) {
      ContributionBinding binding = getOnlyElement(resolvedBindings.contributionBindings());
      if (!binding.bindingKind().equals(ContributionBinding.Kind.SYNTHETIC_MAP)) {
        return BINDING_ELEMENT_NAME.visit(binding.bindingElement());
      }
    }
    return KeyVariableNamer.INSTANCE.apply(bindingKey.key());
  }

  private static final ElementVisitor<String, Void> BINDING_ELEMENT_NAME =
      new ElementKindVisitor6<String, Void>() {
        @Override
        public String visitExecutableAsConstructor(ExecutableElement e, Void p) {
          return visit(e.getEnclosingElement());
        }

        @Override
        public String visitExecutableAsMethod(ExecutableElement e, Void p) {
          return e.getSimpleName().toString();
        }

        @Override
        public String visitType(TypeElement e, Void p) {
          return CaseFormat.UPPER_CAMEL.to(
              CaseFormat.LOWER_CAMEL, e.getSimpleName().toString());
        }
      };

  abstract com.squareup.javapoet.ParameterizedTypeName javapoetFrameworkType();
  abstract ParameterizedTypeName frameworkType();
  abstract String name();
}
