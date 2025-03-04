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

import static androidx.room.compiler.codegen.XTypeNameKt.toJavaPoet;
import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_INJECTION;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static javax.lang.model.SourceVersion.isName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XTypeNames;
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
        dependency -> {
          XClassName frameworkClassName =
              frameworkTypeMapper.getFrameworkType(dependency.kind()).frameworkClassName();
          return FrameworkField.create(
              DependencyVariableNamer.name(dependency),
              frameworkClassName,
              dependency.key().type().xprocessing());
        });
  }

  public CodeBlock frameworkTypeUsageStatement(
      CodeBlock frameworkTypeMemberSelect, RequestKind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlock.of(
            "$T.lazy($L)",
            toJavaPoet(XTypeNames.DOUBLE_CHECK),
            frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return CodeBlock.of("$L.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
        return frameworkTypeMemberSelect;
      case PROVIDER_OF_LAZY:
        return CodeBlock.of(
            "$T.create($L)", toJavaPoet(XTypeNames.PROVIDER_OF_LAZY), frameworkTypeMemberSelect);
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

  public static String generatedProxyMethodName(ContributionBinding binding) {
    switch (binding.kind()) {
      case INJECTION:
      case ASSISTED_INJECTION:
        return "newInstance";
      case PROVISION:
        XMethodElement method = asMethod(binding.bindingElement().get());
        String simpleName = getSimpleName(method);
        // If the simple name is already defined in the factory, prepend "proxy" to the name.
        return simpleName.contentEquals("get") || simpleName.contentEquals("create")
            ? "proxy" + LOWER_CAMEL.to(UPPER_CAMEL, simpleName)
            : simpleName;
      default:
        throw new AssertionError("Unexpected binding kind: " + binding);
    }
  }

  /** Returns the generated factory or members injector name for a binding. */
  public static XClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.kind()) {
      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
      case PRODUCTION:
        return factoryNameForElement(asExecutable(binding.bindingElement().get()));
      case ASSISTED_FACTORY:
        return siblingClassName(asTypeElement(binding.bindingElement().get()), "_Impl");
      case MEMBERS_INJECTION:
        return membersInjectorNameForType(
            ((MembersInjectionBinding) binding).membersInjectedType());
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the generated factory name for the given element.
   *
   * <p>This method is useful during validation before a {@link Binding} can be created. If a
   * binding already exists for the given element, prefer to call {@link
   * #generatedClassNameForBinding(Binding)} instead since this method does not validate that the
   * given element is actually a binding element or not.
   */
  public static XClassName factoryNameForElement(XExecutableElement element) {
    return elementBasedClassName(element, "Factory");
  }

  /**
   * Calculates an appropriate {@link XClassName} for a generated class that is based on {@code
   * element}, appending {@code suffix} at the end.
   *
   * <p>This will always return a top level class name, even if {@code element}'s enclosing class is
   * a nested type.
   */
  public static XClassName elementBasedClassName(XExecutableElement element, String suffix) {
    XClassName enclosingClassName = element.getEnclosingElement().asClassName();
    String methodName =
        isConstructor(element) ? "" : LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(element));
    return XClassName.Companion.get(
        enclosingClassName.getPackageName(),
        classFileName(enclosingClassName) + "_" + methodName + suffix);
  }

  public static XTypeName parameterizedGeneratedTypeNameForBinding(Binding binding) {
    XClassName className = generatedClassNameForBinding(binding);
    ImmutableList<XTypeName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    return typeParameters.isEmpty()
        ? className
        : className.parametrizedBy(Iterables.toArray(typeParameters, XTypeName.class));
  }

  public static XClassName membersInjectorNameForType(XTypeElement typeElement) {
    return siblingClassName(typeElement, "_MembersInjector");
  }

  public static String memberInjectedFieldSignatureForVariable(XFieldElement field) {
    return field.getEnclosingElement().getClassName().canonicalName() + "." + getSimpleName(field);
  }

  /*
   * TODO(ronshapiro): this isn't perfect, as collisions could still exist. Some examples:
   *
   *  - @Inject void members() {} will generate a method that conflicts with the instance
   *    method `injectMembers(T)`
   *  - Adding the index could conflict with another member:
   *      @Inject void a(Object o) {}
   *      @Inject void a(String s) {}
   *      @Inject void a1(String s) {}
   *
   *    Here, Method a(String) will add the suffix "1", which will conflict with the method
   *    generated for a1(String)
   *  - Members named "members" or "methods" could also conflict with the {@code static} injection
   *    method.
   */
  public static String membersInjectorMethodName(InjectionSite injectionSite) {
    int index = injectionSite.indexAmongAtInjectMembersWithSameSimpleName();
    String indexString = index == 0 ? "" : String.valueOf(index + 1);
    return "inject"
        + LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(injectionSite.element()))
        + indexString;
  }

  public static String classFileName(XClassName className) {
    return CLASS_FILE_NAME_JOINER.join(className.getSimpleNames());
  }

  public static XClassName generatedMonitoringModuleName(XTypeElement componentElement) {
    return siblingClassName(componentElement, "_MonitoringModule");
  }

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static XClassName siblingClassName(XTypeElement typeElement, String suffix) {
    XClassName className = typeElement.asClassName();
    return XClassName.Companion.get(className.getPackageName(), classFileName(className) + suffix);
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
  public static XClassName setFactoryClassName(MultiboundSetBinding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
        return XTypeNames.SET_FACTORY;
      case PRODUCTION:
        SetType setType = SetType.from(binding.key());
        return setType.elementsAreTypeOf(XTypeNames.PRODUCED)
            ? XTypeNames.SET_OF_PRODUCED_PRODUCER
            : XTypeNames.SET_PRODUCER;
      default:
        throw new IllegalArgumentException(binding.bindingType().toString());
    }
  }

  /** The {@link java.util.Map} factory class name appropriate for map bindings. */
  public static XClassName mapFactoryClassName(MultiboundMapBinding binding) {
    MapType mapType = MapType.from(binding.key());
    switch (binding.bindingType()) {
      case PROVISION:
        return mapType.valuesAreProvider()
            ? XTypeNames.MAP_PROVIDER_FACTORY : XTypeNames.MAP_FACTORY;
      case PRODUCTION:
        return mapType.valuesAreFrameworkType()
            ? mapType.valuesAreTypeOf(XTypeNames.PRODUCER)
                ? XTypeNames.MAP_OF_PRODUCER_PRODUCER
                : XTypeNames.MAP_OF_PRODUCED_PRODUCER
            : XTypeNames.MAP_PRODUCER;
      default:
        throw new IllegalArgumentException(binding.bindingType().toString());
    }
  }

  public static ImmutableList<XTypeName> bindingTypeElementTypeVariableNames(Binding binding) {
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
    return simpleVariableName(typeElement.asClassName());
  }

  /**
   * Returns a name to be used for variables of the given {@link XClassName}. Prefer semantically
   * meaningful variable names, but if none can be derived, this will produce something readable.
   */
  public static String simpleVariableName(XClassName className) {
    String candidateName = UPPER_CAMEL.to(LOWER_CAMEL, className.getSimpleName());
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
