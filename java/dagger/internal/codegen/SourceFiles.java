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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.Optionals.optionalComparator;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.PROVIDER_OF_LAZY;
import static java.util.Comparator.comparing;
import static javax.lang.model.SourceVersion.isName;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.Comparator;
import java.util.Iterator;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

/**
 * Utilities for generating files.
 *
 * @author Gregory Kick
 * @since 2.0
 */
class SourceFiles {

  private static final Joiner CLASS_FILE_NAME_JOINER = Joiner.on('_');

  /**
   * Sorts {@link DependencyRequest} instances in an order likely to reflect their logical
   * importance.
   */
  static final Comparator<DependencyRequest> DEPENDENCY_ORDERING =
      // put fields before parameters
      comparing(
              (DependencyRequest request) -> request.requestElement().map(Element::getKind),
              optionalComparator())
          // order by dependency kind
          .thenComparing(DependencyRequest::kind)
          // then sort by name
          .thenComparing(
              request ->
                  request.requestElement().map(element -> element.getSimpleName().toString()),
              optionalComparator());

  /**
   * Generates names and keys for the factory class fields needed to hold the framework classes for
   * all of the dependencies of {@code binding}. It is responsible for choosing a name that
   *
   * <ul>
   * <li>represents all of the dependency requests for this key
   * <li>is <i>probably</i> associated with the type being bound
   * <li>is unique within the class
   * </ul>
   *
   * @param binding must be an unresolved binding (type parameters must match its type element's)
   */
  static ImmutableMap<BindingKey, FrameworkField> generateBindingFieldsForDependencies(
      Binding binding) {
    checkArgument(!binding.unresolved().isPresent(), "binding must be unresolved: %s", binding);

    ImmutableMap.Builder<BindingKey, FrameworkField> bindingFields = ImmutableMap.builder();
    for (Binding.DependencyAssociation dependencyAssociation : binding.dependencyAssociations()) {
      FrameworkDependency frameworkDependency = dependencyAssociation.frameworkDependency();
      bindingFields.put(
          frameworkDependency.bindingKey(),
          FrameworkField.create(
              ClassName.get(frameworkDependency.frameworkClass()),
              TypeName.get(frameworkDependency.bindingKey().key().type()),
              fieldNameForDependency(dependencyAssociation.dependencyRequests())));
    }
    return bindingFields.build();
  }

  private static String fieldNameForDependency(ImmutableSet<DependencyRequest> dependencyRequests) {
    // collect together all of the names that we would want to call the provider
    ImmutableSet<String> dependencyNames =
        FluentIterable.from(dependencyRequests).transform(new DependencyVariableNamer()).toSet();

    if (dependencyNames.size() == 1) {
      // if there's only one name, great! use it!
      return Iterables.getOnlyElement(dependencyNames);
    } else {
      // in the event that a field is being used for a bunch of deps with different names,
      // add all the names together with "And"s in the middle. E.g.: stringAndS
      Iterator<String> namesIterator = dependencyNames.iterator();
      String first = namesIterator.next();
      StringBuilder compositeNameBuilder = new StringBuilder(first);
      while (namesIterator.hasNext()) {
        compositeNameBuilder
            .append("And")
            .append(CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
      }
      return compositeNameBuilder.toString();
    }
  }

  static CodeBlock frameworkTypeUsageStatement(
      CodeBlock frameworkTypeMemberSelect, DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlock.of("$T.lazy($L)", DOUBLE_CHECK, frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return CodeBlock.of("$L.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
      case MEMBERS_INJECTOR:
        return frameworkTypeMemberSelect;
      case PROVIDER_OF_LAZY:
        return CodeBlock.of("$T.create($L)", PROVIDER_OF_LAZY, frameworkTypeMemberSelect);
      default: // including PRODUCED
        throw new AssertionError(dependencyKind);
    }
  }

  /**
   * Returns the generated factory or members injector name for a binding.
   */
  static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contribution = (ContributionBinding) binding;
        checkArgument(contribution.bindingTypeElement().isPresent());
        ClassName enclosingClassName = ClassName.get(contribution.bindingTypeElement().get());
        switch (contribution.bindingKind()) {
          case INJECTION:
          case PROVISION:
          case PRODUCTION:
            return enclosingClassName
                .topLevelClassName()
                .peerClass(
                    classFileName(enclosingClassName)
                        + "_"
                        + factoryPrefix(contribution)
                        + "Factory");

          default:
            throw new AssertionError();
        }

      case MEMBERS_INJECTION:
        return membersInjectorNameForType(
            ((MembersInjectionBinding) binding).membersInjectedType());

      default:
        throw new AssertionError();
    }
  }

  static TypeName parameterizedGeneratedTypeNameForBinding(Binding binding) {
    ClassName className = generatedClassNameForBinding(binding);
    ImmutableList<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    return typeParameters.isEmpty()
        ? className
        : ParameterizedTypeName.get(className, Iterables.toArray(typeParameters, TypeName.class));
  }

  static ClassName membersInjectorNameForType(TypeElement typeElement) {
    return siblingClassName(typeElement,  "_MembersInjector");
  }

  static String classFileName(ClassName className) {
    return CLASS_FILE_NAME_JOINER.join(className.simpleNames());
  }

  static ClassName generatedMonitoringModuleName(
      TypeElement componentElement) {
    return siblingClassName(componentElement, "_MonitoringModule");
  }

  static ClassName generatedProductionExecutorModuleName(TypeElement componentElement) {
    return siblingClassName(componentElement, "_ProductionExecutorModule");
  }

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static ClassName siblingClassName(TypeElement typeElement, String suffix) {
    ClassName className = ClassName.get(typeElement);
    return className.topLevelClassName().peerClass(classFileName(className) + suffix);
  }

  private static String factoryPrefix(ContributionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";

      case PROVISION:
      case PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(
            UPPER_CAMEL, binding.bindingElement().get().getSimpleName().toString());

      default:
        throw new IllegalArgumentException();
    }
  }

  static ImmutableList<TypeVariableName> bindingTypeElementTypeVariableNames(Binding binding) {
    if (binding instanceof ContributionBinding) {
      ContributionBinding contributionBinding = (ContributionBinding) binding;
      if (!contributionBinding.bindingKind().equals(INJECTION)
          && !contributionBinding.requiresModuleInstance()) {
        return ImmutableList.of();
      }
    }
    ImmutableList.Builder<TypeVariableName> builder = ImmutableList.builder();
    for (TypeParameterElement typeParameter :
        binding.bindingTypeElement().get().getTypeParameters()) {
      builder.add(TypeVariableName.get(typeParameter));
    }
    return builder.build();
  }

  /**
   * Returns a name to be used for variables of the given {@linkplain TypeElement type}. Prefer
   * semantically meaningful variable names, but if none can be derived, this will produce something
   * readable.
   */
  // TODO(gak): maybe this should be a function of TypeMirrors instead of Elements?
  static String simpleVariableName(TypeElement typeElement) {
    String candidateName = UPPER_CAMEL.to(LOWER_CAMEL, typeElement.getSimpleName().toString());
    String variableName = protectAgainstKeywords(candidateName);
    verify(isName(variableName), "'%s' was expected to be a valid variable name");
    return variableName;
  }

  private static String protectAgainstKeywords(String candidateName) {
    switch (candidateName) {
      case "package":
        return "pkg";
      case "boolean":
        return "b";
      case "double":
        return "d";
      case "byte":
        return "b";
      case "int":
        return "i";
      case "short":
        return "s";
      case "char":
        return "c";
      case "void":
        return "v";
      case "class":
        return "clazz";
      case "float":
        return "f";
      case "long":
        return "l";
      default:
        return SourceVersion.isKeyword(candidateName) ? candidateName + '_' : candidateName;
    }
  }

  private SourceFiles() {}
}
