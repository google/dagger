/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleSubcomponents;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGER;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS;
import static dagger.internal.codegen.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.MoreAnnotationMirrors.simpleName;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Formats a {@link BindingDeclaration} into a {@link String} suitable for use in error messages.
 */
final class BindingDeclarationFormatter extends Formatter<BindingDeclaration> {
  private static final ImmutableSet<TypeKind> FORMATTABLE_ELEMENT_TYPE_KINDS =
      immutableEnumSet(EXECUTABLE, DECLARED);

  private static final ImmutableSet<ContributionBinding.Kind>
      FORMATTABLE_ELEMENTLESS_BINDING_KINDS =
          immutableEnumSet(
              SYNTHETIC_RELEASABLE_REFERENCE_MANAGER, SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS);

  private final MethodSignatureFormatter methodSignatureFormatter;

  BindingDeclarationFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  /**
   * Returns {@code true} for declarations that this formatter can format. Specifically:
   *
   * <ul>
   * <li>Those with {@linkplain BindingDeclaration#bindingElement() binding elements} that are
   *     methods, constructors, or types.
   * <li>{@link ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGER} bindings.
   * <li>{@link ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS} bindings.
   * </ul>
   */
  boolean canFormat(BindingDeclaration bindingDeclaration) {
    if (bindingDeclaration instanceof SubcomponentDeclaration) {
      return true;
    }
    if (bindingDeclaration.bindingElement().isPresent()) {
      return FORMATTABLE_ELEMENT_TYPE_KINDS.contains(
          bindingDeclaration.bindingElement().get().asType().getKind());
    }
    if (bindingDeclaration instanceof ContributionBinding) {
      ContributionBinding contributionBinding = (ContributionBinding) bindingDeclaration;
      return FORMATTABLE_ELEMENTLESS_BINDING_KINDS.contains(contributionBinding.bindingKind());
    }
    return false;
  }

  @Override
  public String format(BindingDeclaration bindingDeclaration) {
    if (bindingDeclaration instanceof SubcomponentDeclaration) {
      return formatSubcomponentDeclaration((SubcomponentDeclaration) bindingDeclaration);
    }

    if (bindingDeclaration instanceof ContributionBinding) {
      ContributionBinding binding = (ContributionBinding) bindingDeclaration;
      switch (binding.bindingKind()) {
        case SYNTHETIC_RELEASABLE_REFERENCE_MANAGER:
          return String.format(
              "binding for %s from the scope declaration",
              stripCommonTypePrefixes(binding.key().toString()));
        case SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS:
          return String.format(
              "Dagger-generated binding for %s",
              stripCommonTypePrefixes(binding.key().toString()));
        default:
          break;
      }
    }

    checkArgument(
        bindingDeclaration.bindingElement().isPresent(),
        "Cannot format bindings without source elements: %s",
        bindingDeclaration);

    Element bindingElement = bindingDeclaration.bindingElement().get();
    switch (bindingElement.asType().getKind()) {
      case EXECUTABLE:
        return methodSignatureFormatter.format(
            MoreElements.asExecutable(bindingElement),
            bindingDeclaration
                .contributingModule()
                .map(module -> MoreTypes.asDeclared(module.asType())));
      case DECLARED:
        return stripCommonTypePrefixes(bindingElement.asType().toString());
      default:
        throw new IllegalArgumentException("Formatting unsupported for element: " + bindingElement);
    }
  }

  private String formatSubcomponentDeclaration(SubcomponentDeclaration subcomponentDeclaration) {
    ImmutableList<TypeMirror> moduleSubcomponents =
        getModuleSubcomponents(subcomponentDeclaration.moduleAnnotation());
    int index =
        Iterables.indexOf(
            moduleSubcomponents,
            MoreTypes.equivalence()
                .equivalentTo(subcomponentDeclaration.subcomponentType().asType()));
    StringBuilder annotationValue = new StringBuilder();
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("{");
    }
    annotationValue.append(
        formatArgumentInList(
            index,
            moduleSubcomponents.size(),
            subcomponentDeclaration.subcomponentType().getQualifiedName() + ".class"));
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("}");
    }

    return String.format(
        "@%s(subcomponents = %s) for %s",
        simpleName(subcomponentDeclaration.moduleAnnotation()),
        annotationValue,
        subcomponentDeclaration.contributingModule().get());
  }
}
