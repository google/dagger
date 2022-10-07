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
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER_OF_LAZY;
import static dagger.internal.codegen.javapoet.TypeNames.SET_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.SET_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.SET_PRODUCER;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.spi.model.BindingKind.ASSISTED_INJECTION;
import static dagger.spi.model.BindingKind.INJECTION;
import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;
import static javax.lang.model.SourceVersion.isName;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XTypeElement;
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
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.RequestKind;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;

/** Utilities for generating files. */
public final class SourceFiles {

  private static final Joiner CLASS_FILE_NAME_JOINER = Joiner.on('_');

  @Inject SourceFiles() {}

  /**
   * Generates names and keys for the factory class fields needed to hold the framework classes for
   * all of the dependencies of {@code binding}. It is responsible for choosing a name that
   *
   * <ul>
   *   <li>represents all of the dependency requests for this key
   *   <li>is <i>probably</i> associated with the type being bound
   *   <li>is unique within the class
   * </ul>
   *
   * @param binding must be an unresolved binding (type parameters must match its type element's)
   */
  public static ImmutableMap<DependencyRequest, FrameworkField>
      generateBindingFieldsForDependencies(Binding binding) {
    checkArgument(!binding.unresolved().isPresent(), "binding must be unresolved: %s", binding);

    FrameworkTypeMapper frameworkTypeMapper =
        FrameworkTypeMapper.forBindingType(binding.bindingType());

    return Maps.toMap(
        binding.dependencies(),
        dependency ->
            FrameworkField.create(
                frameworkTypeMapper.getFrameworkType(dependency.kind()).frameworkClassName(),
                dependency.key().type().xprocessing().getTypeName(),
                DependencyVariableNamer.name(dependency)));
  }

  public CodeBlock frameworkTypeUsageStatement(
      CodeBlock frameworkTypeMemberSelect, RequestKind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlock.of(
            "$T.lazy($L)",
            DOUBLE_CHECK,
            frameworkTypeMemberSelect);
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
  public ImmutableMap<DependencyRequest, CodeBlock> frameworkFieldUsages(
      ImmutableSet<DependencyRequest> dependencies,
      ImmutableMap<DependencyRequest, FieldSpec> fields) {
    return Maps.toMap(
        dependencies,
        dep -> frameworkTypeUsageStatement(CodeBlock.of("$N", fields.get(dep)), dep.kind()));
  }

  /** Returns the generated factory or members injector name for a binding. */
  public static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contribution = (ContributionBinding) binding;
        switch (contribution.kind()) {
          case ASSISTED_INJECTION:
          case INJECTION:
          case PROVISION:
          case PRODUCTION:
            return factoryNameForElement(asExecutable(binding.bindingElement().get()));

          case ASSISTED_FACTORY:
            return siblingClassName(asTypeElement(binding.bindingElement().get()), "_Impl");

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
   * Returns the generated factory name for the given element.
   *
   * <p>This method is useful during validation before a {@link Binding} can be created. If a
   * binding already exists for the given element, prefer to call {@link
   * #generatedClassNameForBinding(Binding)} instead since this method does not validate that the
   * given element is actually a binding element or not.
   */
  public static ClassName factoryNameForElement(XExecutableElement element) {
    return elementBasedClassName(element, "Factory");
  }

  /**
   * Calculates an appropriate {@link ClassName} for a generated class that is based on {@code
   * element}, appending {@code suffix} at the end.
   *
   * <p>This will always return a {@linkplain ClassName#topLevelClassName() top level class name},
   * even if {@code element}'s enclosing class is a nested type.
   */
  public static ClassName elementBasedClassName(XExecutableElement element, String suffix) {
    ClassName enclosingClassName = element.getEnclosingElement().getClassName();
    String methodName =
        isConstructor(element) ? "" : LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(element));
    return ClassName.get(
        enclosingClassName.packageName(),
        classFileName(enclosingClassName) + "_" + methodName + suffix);
  }

  public static TypeName parameterizedGeneratedTypeNameForBinding(Binding binding) {
    ClassName className = generatedClassNameForBinding(binding);
    ImmutableList<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    return typeParameters.isEmpty()
        ? className
        : ParameterizedTypeName.get(className, Iterables.toArray(typeParameters, TypeName.class));
  }

  public static ClassName membersInjectorNameForType(XTypeElement typeElement) {
    return siblingClassName(typeElement, "_MembersInjector");
  }

  public static String memberInjectedFieldSignatureForVariable(XFieldElement field) {
    return field.getEnclosingElement().getClassName().canonicalName() + "." + getSimpleName(field);
  }

  public static String classFileName(ClassName className) {
    return CLASS_FILE_NAME_JOINER.join(className.simpleNames());
  }

  public static ClassName generatedMonitoringModuleName(XTypeElement componentElement) {
    return siblingClassName(componentElement, "_MonitoringModule");
  }

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static ClassName siblingClassName(XTypeElement typeElement, String suffix) {
    ClassName className = typeElement.getClassName();
    return className.topLevelClassName().peerClass(classFileName(className) + suffix);
  }

  /**
   * The {@link java.util.Set} factory class name appropriate for set bindings.
   *
   * <ul>
   *   <li>{@link dagger.producers.internal.SetFactory} for provision bindings.
   *   <li>{@link dagger.producers.internal.SetProducer} for production bindings for {@code Set<T>}.
   *   <li>{@link dagger.producers.internal.SetOfProducedProducer} for production bindings for
   *       {@code Set<Produced<T>>}.
   * </ul>
   */
  public static ClassName setFactoryClassName(ContributionBinding binding) {
    checkArgument(binding.kind().equals(MULTIBOUND_SET));
    if (binding.bindingType().equals(BindingType.PROVISION)) {
      return SET_FACTORY;
    } else {
      SetType setType = SetType.from(binding.key());
      return setType.elementsAreTypeOf(TypeNames.PRODUCED)
          ? SET_OF_PRODUCED_PRODUCER
          : SET_PRODUCER;
    }
  }

  /** The {@link java.util.Map} factory class name appropriate for map bindings. */
  public static ClassName mapFactoryClassName(ContributionBinding binding) {
    checkState(binding.kind().equals(MULTIBOUND_MAP), binding.kind());
    MapType mapType = MapType.from(binding.key());
    switch (binding.bindingType()) {
      case PROVISION:
        return mapType.valuesAreTypeOf(PROVIDER) ? MAP_PROVIDER_FACTORY : MAP_FACTORY;
      case PRODUCTION:
        return mapType.valuesAreFrameworkType()
            ? mapType.valuesAreTypeOf(PRODUCER)
                ? MAP_OF_PRODUCER_PRODUCER
                : MAP_OF_PRODUCED_PRODUCER
            : MAP_PRODUCER;
      default:
        throw new IllegalArgumentException(binding.bindingType().toString());
    }
  }

  public static ImmutableList<TypeVariableName> bindingTypeElementTypeVariableNames(
      Binding binding) {
    if (binding instanceof ContributionBinding) {
      ContributionBinding contributionBinding = (ContributionBinding) binding;
      if (!(contributionBinding.kind() == INJECTION
              || contributionBinding.kind() == ASSISTED_INJECTION)
          && !contributionBinding.requiresModuleInstance()) {
        return ImmutableList.of();
      }
    }
    return typeVariableNames(binding.bindingTypeElement().get());
  }

  /**
   * Returns a name to be used for variables of the given {@linkplain XTypeElement type}. Prefer
   * semantically meaningful variable names, but if none can be derived, this will produce something
   * readable.
   */
  // TODO(gak): maybe this should be a function of TypeMirrors instead of Elements?
  public static String simpleVariableName(XTypeElement typeElement) {
    return simpleVariableName(typeElement.getClassName());
  }

  /**
   * Returns a name to be used for variables of the given {@linkplain ClassName}. Prefer
   * semantically meaningful variable names, but if none can be derived, this will produce something
   * readable.
   */
  public static String simpleVariableName(ClassName className) {
    String candidateName = UPPER_CAMEL.to(LOWER_CAMEL, className.simpleName());
    String variableName = protectAgainstKeywords(candidateName);
    verify(isName(variableName), "'%s' was expected to be a valid variable name", variableName);
    return variableName;
  }

  public static String protectAgainstKeywords(String candidateName) {
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
}
