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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.Optionals.optionalComparator;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER_OF_LAZY;
import static dagger.internal.codegen.javapoet.TypeNames.SET_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.SET_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.SET_PRODUCER;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static dagger.model.BindingKind.MULTIBOUND_SET;
import static java.util.Comparator.comparing;
import static javax.lang.model.SourceVersion.isName;

import com.google.auto.common.MoreElements;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.SetFactory;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.inject.Provider;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

/**
 * Utilities for generating files.
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
  static ImmutableMap<Key, FrameworkField> generateBindingFieldsForDependencies(
      Binding binding) {
    checkArgument(!binding.unresolved().isPresent(), "binding must be unresolved: %s", binding);

    ImmutableMap.Builder<Key, FrameworkField> bindingFields = ImmutableMap.builder();
    for (Binding.DependencyAssociation dependencyAssociation : binding.dependencyAssociations()) {
      FrameworkDependency frameworkDependency = dependencyAssociation.frameworkDependency();
      bindingFields.put(
          frameworkDependency.key(),
          FrameworkField.create(
              ClassName.get(frameworkDependency.frameworkClass()),
              TypeName.get(frameworkDependency.key().type()),
              fieldNameForDependency(dependencyAssociation.dependencyRequests())));
    }
    return bindingFields.build();
  }

  private static String fieldNameForDependency(ImmutableSet<DependencyRequest> dependencyRequests) {
    // collect together all of the names that we would want to call the provider
    ImmutableSet<String> dependencyNames =
        dependencyRequests.stream().map(DependencyVariableNamer::name).collect(toImmutableSet());

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
      CodeBlock frameworkTypeMemberSelect, RequestKind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlock.of("$T.lazy($L)", DOUBLE_CHECK, frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return CodeBlock.of("$L.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
        return frameworkTypeMemberSelect;
      case PROVIDER_OF_LAZY:
        return CodeBlock.of("$T.create($L)", PROVIDER_OF_LAZY, frameworkTypeMemberSelect);
      default: // including PRODUCED
        throw new AssertionError(dependencyKind);
    }
  }

  /**
   * Returns a mapping of {@link DependencyRequest}s to {@link CodeBlock}s that {@linkplain
   * #frameworkTypeUsageStatement(CodeBlock, RequestKind) use them}.
   */
  static ImmutableMap<DependencyRequest, CodeBlock> frameworkFieldUsages(
      ImmutableSet<DependencyRequest> dependencies, ImmutableMap<Key, FieldSpec> fields) {
    return Maps.toMap(
        dependencies,
        dep ->
            frameworkTypeUsageStatement(CodeBlock.of("$N", fields.get(dep.key())), dep.kind()));
  }

  /**
   * Returns the generated factory or members injector name for a binding.
   */
  static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contribution = (ContributionBinding) binding;
        switch (contribution.kind()) {
          case INJECTION:
          case PROVISION:
          case PRODUCTION:
            return elementBasedClassName(
                MoreElements.asExecutable(binding.bindingElement().get()), "Factory");

          default:
            throw new AssertionError();
        }

      case MEMBERS_INJECTION:
        return membersInjectorNameForType(
            ((MembersInjectionBinding) binding).membersInjectedType());
    }
    throw new AssertionError();
  }

  /**
   * Calculates an appropriate {@link ClassName} for a generated class that is based on {@code
   * element}, appending {@code suffix} at the end.
   *
   * <p>This will always return a {@linkplain ClassName#topLevelClassName() top level class name},
   * even if {@code element}'s enclosing class is a nested type.
   */
  static ClassName elementBasedClassName(ExecutableElement element, String suffix) {
    ClassName enclosingClassName =
        ClassName.get(MoreElements.asType(element.getEnclosingElement()));
    String methodName =
        element.getKind().equals(ElementKind.CONSTRUCTOR)
            ? ""
            : LOWER_CAMEL.to(UPPER_CAMEL, element.getSimpleName().toString());
    return ClassName.get(
        enclosingClassName.packageName(),
        classFileName(enclosingClassName) + "_" + methodName + suffix);
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

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static ClassName siblingClassName(TypeElement typeElement, String suffix) {
    ClassName className = ClassName.get(typeElement);
    return className.topLevelClassName().peerClass(classFileName(className) + suffix);
  }

  /**
   * The {@link java.util.Set} factory class name appropriate for set bindings.
   *
   * <ul>
   * <li>{@link SetFactory} for provision bindings.
   * <li>{@link SetProducer} for production bindings for {@code Set<T>}.
   * <li>{@link SetOfProducedProducer} for production bindings for {@code Set<Produced<T>>}.
   * </ul>
   */
  static ClassName setFactoryClassName(ContributionBinding binding) {
    checkArgument(binding.kind().equals(MULTIBOUND_SET));
    if (binding.bindingType().equals(BindingType.PROVISION)) {
      return SET_FACTORY;
    } else {
      SetType setType = SetType.from(binding.key());
      return setType.elementsAreTypeOf(Produced.class) ? SET_OF_PRODUCED_PRODUCER : SET_PRODUCER;
    }
  }

  /** The {@link java.util.Map} factory class name appropriate for map bindings. */
  static ClassName mapFactoryClassName(ContributionBinding binding) {
    checkState(binding.kind().equals(MULTIBOUND_MAP), binding.kind());
    MapType mapType = MapType.from(binding.key());
    switch (binding.bindingType()) {
      case PROVISION:
        return mapType.valuesAreTypeOf(Provider.class) ? MAP_PROVIDER_FACTORY : MAP_FACTORY;
      case PRODUCTION:
        return mapType.valuesAreFrameworkType()
            ? mapType.valuesAreTypeOf(Producer.class)
                ? MAP_OF_PRODUCER_PRODUCER
                : MAP_OF_PRODUCED_PRODUCER
            : MAP_PRODUCER;
      default:
        throw new IllegalArgumentException(binding.bindingType().toString());
    }
  }

  static ImmutableList<TypeVariableName> bindingTypeElementTypeVariableNames(Binding binding) {
    if (binding instanceof ContributionBinding) {
      ContributionBinding contributionBinding = (ContributionBinding) binding;
      if (!contributionBinding.kind().equals(INJECTION)
          && !contributionBinding.requiresModuleInstance()) {
        return ImmutableList.of();
      }
    }
    List<? extends TypeParameterElement> typeParameters =
        binding.bindingTypeElement().get().getTypeParameters();
    return typeParameters.stream().map(TypeVariableName::get).collect(toImmutableList());
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

  static String protectAgainstKeywords(String candidateName) {
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
