/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.SourceFiles.generatedProxyClassName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.Accessibility.isExecutableAccessibleFromSubtype;
import static dagger.internal.codegen.xprocessing.Accessibility.isExecutablePubliclyAccessibleFromSubtype;
import static dagger.internal.codegen.xprocessing.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypePubliclyAccessible;
import static dagger.internal.codegen.xprocessing.NullableTypeNames.asNullableTypeName;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.toStableString;
import static dagger.internal.codegen.xprocessing.XTypes.containsTypeParameter;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.codegen.XParameterSpec;
import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.processing.XConstructorElement;
import androidx.room3.compiler.processing.XConstructorType;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XExecutableElement;
import androidx.room3.compiler.processing.XExecutableParameterElement;
import androidx.room3.compiler.processing.XHasModifiers;
import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XMethodType;
import androidx.room3.compiler.processing.XType;
import androidx.room3.compiler.processing.XTypeElement;
import androidx.room3.compiler.processing.XVariableElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.xprocessing.Accessibility;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XParameterSpecs;
import dagger.internal.codegen.xprocessing.XTypeElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.List;
import java.util.Optional;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@link XTypeElement}. */
  public static XMethodElement assistedFactoryMethod(XTypeElement factory) {
    ImmutableSet<XMethodElement> factoryMethods = assistedFactoryMethods(factory);
    if (factoryMethods.size() != 1) {
      // TODO(bcorso): This check can be removed (and rely on Iterables.getOnlyElement() below) once
      // https://github.com/google/dagger/issues/3450#issuecomment-3108716712 is fixed. For now, we
      // use a more verbose, custom error message with more information to make it easier to debug.
      throw new IllegalStateException(
          "Expected exactly one factory method for "
              + toStableString(factory)
              + " but found: "
              + factoryMethods.stream()
                  .map(
                      method ->
                          toStableString(method.getEnclosingElement())
                              + "#"
                              + toStableString(method))
                  .collect(toImmutableList()));
    }
    return getOnlyElement(factoryMethods);
  }

  /** Returns the list of abstract factory methods for the given factory {@link XTypeElement}. */
  public static ImmutableSet<XMethodElement> assistedFactoryMethods(XTypeElement factory) {
    return XTypeElements.getAllNonPrivateInstanceMethods(factory).stream()
        .filter(XHasModifiers::isAbstract)
        .filter(method -> !method.isJavaDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(XTypeElement typeElement) {
    return assistedInjectedConstructors(typeElement).stream()
        .anyMatch(constructor -> constructor.hasAnnotation(XTypeNames.ASSISTED_INJECT));
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(XElement element) {
    return element.hasAnnotation(XTypeNames.ASSISTED_FACTORY);
  }

  /**
   * Returns the list of assisted parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XConstructorType constructorType = constructor.asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static ImmutableList<XParameterSpec> assistedParameterSpecs(
      List<? extends XExecutableParameterElement> paramElements, List<XType> paramTypes) {
    ImmutableList.Builder<XParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      XExecutableParameterElement paramElement = paramElements.get(i);
      XType paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            XParameterSpecs.of(paramElement.getJvmName(), paramType.asTypeName()));
      }
    }
    return assistedParameterSpecs.build();
  }

  /**
   * Returns the list of assisted factory parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedFactoryParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
    XMethodType factoryMethodType =
        metadata.factoryMethod().asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link AssistedInject}. */
  public static ImmutableSet<XConstructorElement> assistedInjectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(constructor -> constructor.hasAnnotation(XTypeNames.ASSISTED_INJECT))
        .collect(toImmutableSet());
  }

  public static ImmutableList<XExecutableParameterElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? asConstructor(binding.bindingElement().get()).getParameters().stream()
            .filter(AssistedInjectionAnnotations::isAssistedParameter)
            .collect(toImmutableList())
        : ImmutableList.of();
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(XVariableElement param) {
    return param.hasAnnotation(XTypeNames.ASSISTED);
  }

  /** Metadata about an {@link dagger.assisted.AssistedFactory} annotated type. */
  @AutoValue
  public abstract static class AssistedFactoryMetadata {
    public static AssistedFactoryMetadata create(XType factoryType) {
      XTypeElement factoryElement = factoryType.getTypeElement();
      XMethodElement factoryMethod = assistedFactoryMethod(factoryElement);
      XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
      XType assistedInjectType = factoryMethodType.getReturnType();
      XTypeElement assistedInjectElement = assistedInjectType.getTypeElement();
      // Always match @Assisted parameters using the unresolved factory type, since a component's
      // resolved factoryType may instantiate two distinct type parameters to the same type (e.g.
      // FooFactory<Dep1, AssistedDep1> where both @Assisted parameters become AssistedDep1).
      XMethodType unresolvedFactoryMethodType = factoryMethod.asMemberOf(factoryElement.getType());
      XType unresolvedAssistedInjectType = unresolvedFactoryMethodType.getReturnType();
      return new AutoValue_AssistedInjectionAnnotations_AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          factoryMethodType,
          assistedInjectElement,
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(
              unresolvedAssistedInjectType),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, unresolvedFactoryMethodType));
    }

    public abstract XTypeElement factory();

    public abstract XType factoryType();

    public abstract XMethodElement factoryMethod();

    public abstract XMethodType factoryMethodType();

    public abstract XTypeElement assistedInjectElement();

    public abstract XType assistedInjectType();

    public abstract ImmutableList<AssistedParameter> assistedInjectAssistedParameters();

    public abstract ImmutableList<AssistedParameter> assistedFactoryAssistedParameters();

    @Memoized
    public ImmutableMap<AssistedParameter, XExecutableParameterElement>
        assistedInjectAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XExecutableParameterElement> builder =
          ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }

    @Memoized
    public ImmutableMap<AssistedParameter, XExecutableParameterElement>
        assistedFactoryAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XExecutableParameterElement> builder =
          ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }

    /**
     * Returns true if a standalone proxy file (e.g. {@code FooFactory_Proxy.java}) must be
     * generated on disk for this assisted factory at factory compile-time.
     *
     * <p>A proxy file is generated if the factory interface, return type, or parameter raw types
     * are non-public, ensuring future components in other packages can implement this factory
     * without visibility errors.
     */
    @Memoized
    public boolean needsToGenerateProxy() {
      // We generate a proxy for any factory with type parameters, e.g. FooFactory<T>, since we
      // don't know whether or not the user will request an inaccessible type, e.g.
      // FooFactory<InaccessibleType>.
      return !factory().getTypeParameters().isEmpty() || needsProxy(Optional.empty());
    }

    /**
     * Returns true if a component generated in {@code accessingPackage} must use the generated
     * proxy file instead of implementing the factory interface directly.
     *
     * <p>Even if a proxy file was generated on disk, a component does not need to use it if the
     * component is in the same package as the factory and its raw types.
     */
    public boolean needsToUseProxy(String accessingPackage) {
      return needsProxy(Optional.of(accessingPackage));
    }

    private boolean needsProxy(Optional<String> accessingPackage) {
      if (!isTypeAccessible(factoryType(), accessingPackage)) {
        return true;
      }
      if (!factory().isInterface()
          && !hasAccessibleNoArgConstructor(factory(), accessingPackage)) {
        return true;
      }
      if (!isAccessibleFromSubtype(factoryMethod(), accessingPackage)) {
        return true;
      }
      if (!isTypeAccessible(assistedInjectType(), accessingPackage)) {
        return true;
      }
      for (XType paramType : factoryMethodType().getParameterTypes()) {
        if (!isTypeAccessible(paramType, accessingPackage)) {
          return true;
        }
      }
      return false;
    }

    private static boolean hasAccessibleNoArgConstructor(
        XTypeElement factory, Optional<String> accessingPackage) {
      Optional<XConstructorElement> noArgConstructor =
          factory.getConstructors().stream()
              .filter(constructor -> constructor.getParameters().isEmpty())
              .findFirst();
      return noArgConstructor.isEmpty()
          || isAccessibleFromSubtype(noArgConstructor.get(), accessingPackage);
    }

    private static boolean isAccessibleFromSubtype(
        XExecutableElement executable, Optional<String> accessingPackage) {
      return accessingPackage.isPresent()
          ? isExecutableAccessibleFromSubtype(executable, accessingPackage.get())
          : isExecutablePubliclyAccessibleFromSubtype(executable);
    }

    private static boolean isTypeAccessible(XType type, Optional<String> accessingPackage) {
      return accessingPackage.isPresent()
          ? Accessibility.isTypeAccessibleFrom(type, accessingPackage.get())
          : isTypePubliclyAccessible(type);
    }

    /** Returns the class name of the generated proxy interface. */
    @Memoized
    public XClassName proxyClassName() {
      return generatedProxyClassName(factory());
    }

    /** Returns the name of the proxy method, which matches the factory method name. */
    @Memoized
    public String proxyMethodName() {
      return factoryMethod().getJvmName();
    }

    /**
     * Returns the name of the proxy instance parameter of {@code createFactory}, chosen so that it
     * doesn't clash with any of the factory method's parameter names.
     */
    @Memoized
    public String proxyInstanceName() {
      UniqueNameSet names = new UniqueNameSet();
      factoryMethod().getParameters().stream()
          .map(XExecutableParameterElement::getJvmName)
          .forEach(names::claim);
      return names.getUniqueName("proxy");
    }

    // TODO(b/578079423): Align proxy parameter types with *_Factory#newInstance parameter types
    // when type parameters are used.
    private static XTypeName proxyType(XType type) {
      if (isTypePubliclyAccessible(type) && !containsTypeParameter(type)) {
        return type.asTypeName();
      } else if (isDeclared(type) && isRawTypePubliclyAccessible(type)) {
        return type.getRawType().asTypeName();
      }
      return XTypeName.ANY_OBJECT;
    }

    /**
     * Returns the return type name for the proxy method.
     *
     * <p>Only non-public types and types that contain type parameters are masked.
     */
    @Memoized
    public XTypeName proxyReturnType() {
      return proxyType(unresolvedFactoryMethodType().getReturnType());
    }

    /**
     * Returns the {@link XParameterSpec}s for the proxy method using the factory method's parameter
     * names.
     */
    public ImmutableList<XParameterSpec> proxyParameterSpecs(CompilerOptions compilerOptions) {
      return proxyParameterSpecs(
          factoryMethod().getParameters().stream()
              .map(XExecutableParameterElement::getJvmName)
              .collect(toImmutableList()),
          compilerOptions);
    }

    /**
     * Returns the {@link XParameterSpec}s for the proxy method using the given {@code
     * parameterNames}.
     *
     * <p>Only non-public types and types that contain type parameters are masked.
     */
    public ImmutableList<XParameterSpec> proxyParameterSpecs(
        List<String> parameterNames, CompilerOptions compilerOptions) {
      List<XExecutableParameterElement> params = factoryMethod().getParameters();
      List<XType> paramTypes = unresolvedFactoryMethodType().getParameterTypes();
      checkArgument(parameterNames.size() == params.size());
      ImmutableList.Builder<XParameterSpec> specs = ImmutableList.builder();
      for (int i = 0; i < params.size(); i++) {
        specs.add(
            proxyParameterSpec(
                parameterNames.get(i), params.get(i), paramTypes.get(i), compilerOptions));
      }
      return specs.build();
    }

    private static XParameterSpec proxyParameterSpec(
        String name,
        XExecutableParameterElement param,
        XType paramType,
        CompilerOptions compilerOptions) {
      Nullability nullability = Nullability.of(param);
      if (isTypePubliclyAccessible(paramType) && !containsTypeParameter(paramType)) {
        return XParameterSpecs.of(name, paramType, nullability, compilerOptions);
      }
      XTypeName maskedTypeName =
          asNullableTypeName(proxyType(paramType), nullability, compilerOptions);
      return XParameterSpecs.builder(name, maskedTypeName)
          .addAnnotationNames(nullability.nonTypeUseNullableAnnotations())
          .build();
    }

    // The proxy method signature must be computed from the unresolved factory type (e.g.
    // `FooFactory<T>`) rather than the component's resolved `factoryType()` (e.g.
    // `FooFactory<String>`) so that the component's `@Override` implementation matches the
    // `FooFactory_Proxy` interface generated on disk.
    @Memoized
    XMethodType unresolvedFactoryMethodType() {
      return factoryMethod().asMemberOf(factory().getType());
    }
  }

  /**
   * Metadata about an {@link Assisted} annotated parameter.
   *
   * <p>This parameter can represent an {@link Assisted} annotated parameter from an {@link
   * AssistedInject} constructor or an {@link AssistedFactory} method.
   */
  @AutoValue
  public abstract static class AssistedParameter {
    public static AssistedParameter create(
        XExecutableParameterElement parameter, XType parameterType) {
      AssistedParameter assistedParameter =
          new AutoValue_AssistedInjectionAnnotations_AssistedParameter(
              Optional.ofNullable(parameter.getAnnotation(XTypeNames.ASSISTED))
                  .map(assisted -> assisted.getAsString("value"))
                  .orElse(""),
              XTypes.equivalence().wrap(parameterType));
      assistedParameter.parameterElement = parameter;
      return assistedParameter;
    }

    private XExecutableParameterElement parameterElement;

    /** Returns the string qualifier from the {@link Assisted#value()}. */
    public abstract String qualifier();

    /** Returns the equivalence wrapper for the type annotated with {@link Assisted}. */
    abstract Equivalence.Wrapper<XType> wrappedType();

    /** Returns the type annotated with {@link Assisted}. */
    public final XType type() {
      return wrappedType().get();
    }

    public final XExecutableParameterElement element() {
      return parameterElement;
    }

    @Override
    public final String toString() {
      return qualifier().isEmpty()
          ? String.format("@Assisted %s", XTypes.toStableString(type()))
          : String.format("@Assisted(\"%s\") %s", qualifier(), XTypes.toStableString(type()));
    }
  }

  public static ImmutableList<AssistedParameter> assistedInjectAssistedParameters(
      XType assistedInjectType) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    XConstructorElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement()));
    XConstructorType assistedInjectConstructorType =
        assistedInjectConstructor.asMemberOf(assistedInjectType.makeNonNullable());

    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      XExecutableParameterElement parameter = assistedInjectConstructor.getParameters().get(i);
      XType parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (parameter.hasAnnotation(XTypeNames.ASSISTED)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder.build();
  }

  private static ImmutableList<AssistedParameter> assistedFactoryAssistedParameters(
      XMethodElement factoryMethod, XMethodType factoryMethodType) {
    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      XExecutableParameterElement parameter = factoryMethod.getParameters().get(i);
      XType parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder.build();
  }

  private AssistedInjectionAnnotations() {}
}
