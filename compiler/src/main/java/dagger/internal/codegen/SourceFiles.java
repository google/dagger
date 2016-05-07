/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package dagger.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.Iterator;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.FrameworkDependency.frameworkDependenciesForBinding;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.PROVIDER_OF_LAZY;

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
  static final Ordering<DependencyRequest> DEPENDENCY_ORDERING = new Ordering<DependencyRequest>() {
    @Override
    public int compare(DependencyRequest left, DependencyRequest right) {
      return ComparisonChain.start()
      // put fields before parameters
          .compare(left.requestElement().getKind(), right.requestElement().getKind())
          // order by dependency kind
          .compare(left.kind(), right.kind())
          // then sort by name
          .compare(left.requestElement().getSimpleName().toString(),
              right.requestElement().getSimpleName().toString()).result();
    }
  };

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
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
      bindingFields.put(
          frameworkDependency.bindingKey(),
          FrameworkField.createWithTypeFromKey(
              frameworkDependency.frameworkClass(),
              frameworkDependency.bindingKey().key(),
              fieldNameForDependency(frameworkDependency)));
    }
    return bindingFields.build();
  }

  private static String fieldNameForDependency(FrameworkDependency frameworkDependency) {
    // collect together all of the names that we would want to call the provider
    ImmutableSet<String> dependencyNames =
        FluentIterable.from(frameworkDependency.dependencyRequests())
            .transform(new DependencyVariableNamer())
            .toSet();

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
        return CodeBlock.of("$L", frameworkTypeMemberSelect);
      case PROVIDER_OF_LAZY:
        return CodeBlock.of("$T.create($L)", PROVIDER_OF_LAZY, frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
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
        checkArgument(!contribution.isSyntheticBinding());
        ClassName enclosingClassName = ClassName.get(contribution.bindingTypeElement());
        switch (contribution.bindingKind()) {
          case INJECTION:
          case PROVISION:
          case IMMEDIATE:
          case FUTURE_PRODUCTION:
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
        return membersInjectorNameForType(binding.bindingTypeElement());

      default:
        throw new AssertionError();
    }
  }

  static TypeName parameterizedGeneratedTypeNameForBinding(
      Binding binding) {
    ClassName className = generatedClassNameForBinding(binding);
    ImmutableList<TypeName> typeParameters = bindingTypeParameters(binding);
    if (typeParameters.isEmpty()) {
      return className;
    } else {
      return ParameterizedTypeName.get(
          className,
          FluentIterable.from(typeParameters).toArray(TypeName.class));
    }
  }

  private static Optional<TypeMirror> typeMirrorForBindingTypeParameters(Binding binding)
      throws AssertionError {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contributionBinding = (ContributionBinding) binding;
        switch (contributionBinding.bindingKind()) {
          case INJECTION:
            return Optional.of(contributionBinding.key().type());

          case PROVISION:
            // For provision bindings, we parameterize creation on the types of
            // the module, not the types of the binding.
            // Consider: Module<A, B, C> { @Provides List<B> provideB(B b) { .. }}
            // The binding is just parameterized on <B>, but we need all of <A, B, C>.
            return Optional.of(contributionBinding.bindingTypeElement().asType());

          case IMMEDIATE:
          case FUTURE_PRODUCTION:
            // TODO(beder): Can these be treated just like PROVISION?
            throw new UnsupportedOperationException();
            
          default:
            return Optional.absent();
        }

      case MEMBERS_INJECTION:
        return Optional.of(binding.key().type());

      default:
        throw new AssertionError();
    }
  }

  static ImmutableList<TypeName> bindingTypeParameters(
      Binding binding) {
    Optional<TypeMirror> typeMirror = typeMirrorForBindingTypeParameters(binding);
    if (!typeMirror.isPresent()) {
      return ImmutableList.of();
    }
    TypeName bindingTypeName = TypeName.get(typeMirror.get());
    return bindingTypeName instanceof ParameterizedTypeName
        ? ImmutableList.copyOf(((ParameterizedTypeName) bindingTypeName).typeArguments)
        : ImmutableList.<TypeName>of();
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
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(
            UPPER_CAMEL, ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());

      default:
        throw new IllegalArgumentException();
    }
  }

  static ImmutableList<TypeVariableName> bindingTypeElementTypeVariableNames(Binding binding) {
    ImmutableList.Builder<TypeVariableName> builder = ImmutableList.builder();
    for (TypeParameterElement typeParameter : binding.bindingTypeElement().getTypeParameters()) {
      builder.add(TypeVariableName.get(typeParameter));
    }
    return builder.build();
  }

  private SourceFiles() {}
}
