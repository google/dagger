/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static androidx.room.compiler.codegen.compat.XConverters.toXPoet;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedParameter;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.generatedProxyMethodName;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorMethodName;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.xprocessing.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.xprocessing.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toConcatenatedCodeBlock;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeNames.asClassName;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XFunSpecs;
import dagger.internal.codegen.xprocessing.XParameterSpecs;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Convenience methods for creating and invoking {@link InjectionMethod}s. */
final class InjectionMethods {
  private InjectionMethods() {}

  /**
   * A method that returns an object from a {@code @Provides} method or an {@code @Inject}ed
   * constructor. Its parameters match the dependency requests for constructor and members
   * injection.
   *
   * <p>For {@code @Provides} methods named "foo", the method name is "proxyFoo". For example:
   *
   * <pre><code>
   * abstract class FooModule {
   *   {@literal @Provides} static Foo provideFoo(Bar bar, Baz baz) { … }
   * }
   *
   * public static proxyProvideFoo(Bar bar, Baz baz) { … }
   * </code></pre>
   *
   * <p>For {@code @Inject}ed constructors, the method name is "newFoo". For example:
   *
   * <pre><code>
   * class Foo {
   *   {@literal @Inject} Foo(Bar bar) {}
   * }
   *
   * public static Foo newFoo(Bar bar) { … }
   * </code></pre>
   */
  static final class ProvisionMethod {

    /**
     * Invokes the injection method for {@code binding}, with the dependencies transformed with the
     * {@code dependencyUsage} function.
     */
    static XCodeBlock invoke(
        ContributionBinding binding,
        Function<DependencyRequest, XCodeBlock> injectedDependencyUsage,
        Function<XExecutableParameterElement, XCodeBlock> assistedDependencyUsage,
        XClassName requestingClass,
        Optional<XCodeBlock> moduleReference,
        CompilerOptions compilerOptions) {
      ImmutableList.Builder<XCodeBlock> arguments = ImmutableList.builder();
      moduleReference.ifPresent(arguments::add);
      invokeArguments(binding, injectedDependencyUsage, assistedDependencyUsage)
          .forEach(arguments::add);

      XClassName enclosingClass = generatedClassNameForBinding(binding);
      String methodName = generatedProxyMethodName(binding);
      return invokeMethod(methodName, arguments.build(), enclosingClass, requestingClass);
    }

    static ImmutableList<XCodeBlock> invokeArguments(
        ContributionBinding binding,
        Function<DependencyRequest, XCodeBlock> injectedDependencyUsage,
        Function<XExecutableParameterElement, XCodeBlock> assistedDependencyUsage) {
      ImmutableMap<XExecutableParameterElement, DependencyRequest> dependencyRequestMap =
          provisionDependencies(binding).stream()
              .collect(
                  toImmutableMap(
                      request -> asMethodParameter(request.requestElement().get().xprocessing()),
                      request -> request));

      ImmutableList.Builder<XCodeBlock> arguments = ImmutableList.builder();
      XExecutableElement method = asExecutable(binding.bindingElement().get());
      for (XExecutableParameterElement parameter : method.getParameters()) {
        if (isAssistedParameter(parameter)) {
          arguments.add(assistedDependencyUsage.apply(parameter));
        } else if (dependencyRequestMap.containsKey(parameter)) {
          DependencyRequest request = dependencyRequestMap.get(parameter);
          arguments.add(injectedDependencyUsage.apply(request));
        } else {
          throw new AssertionError("Unexpected parameter: " + parameter);
        }
      }

      return arguments.build();
    }

    private static ImmutableSet<DependencyRequest> provisionDependencies(
        ContributionBinding binding) {
      switch (binding.kind()) {
        case INJECTION:
          return ((InjectionBinding) binding).constructorDependencies();
        case ASSISTED_INJECTION:
          return ((AssistedInjectionBinding) binding).constructorDependencies();
        case PROVISION:
          return ((ProvisionBinding) binding).dependencies();
        default:
          throw new AssertionError("Unexpected binding kind: " + binding.kind());
      }
    }
  }

  /**
   * A static method that injects one member of an instance of a type. Its first parameter is an
   * instance of the type to be injected. The remaining parameters match the dependency requests for
   * the injection site.
   *
   * <p>Example:
   *
   * <pre><code>
   * class Foo {
   *   {@literal @Inject} Bar bar;
   *   {@literal @Inject} void setThings(Baz baz, Qux qux) {}
   * }
   *
   * public static injectBar(Foo instance, Bar bar) { … }
   * public static injectSetThings(Foo instance, Baz baz, Qux qux) { … }
   * </code></pre>
   */
  static final class InjectionSiteMethod {
    /**
     * Invokes each of the injection methods for {@code injectionSites}, with the dependencies
     * transformed using the {@code dependencyUsage} function.
     *
     * @param instanceType the type of the {@code instance} parameter
     */
    static XCodeBlock invokeAll(
        ImmutableSet<InjectionSite> injectionSites,
        XClassName generatedTypeName,
        XCodeBlock instanceCodeBlock,
        XType instanceType,
        Function<DependencyRequest, XCodeBlock> dependencyUsage) {
      return injectionSites.stream()
          .map(
              injectionSite -> {
                XType injectSiteType = injectionSite.enclosingTypeElement().getType();

                // If instance has been declared as Object because it is not accessible from the
                // component, but the injectionSite is in a supertype of instanceType that is
                // publicly accessible, the InjectionSiteMethod will request the actual type and not
                // Object as the first parameter. If so, cast to the supertype which is accessible
                // from within generatedTypeName
                XCodeBlock maybeCastedInstance =
                    instanceType.asTypeName().equals(XTypeName.ANY_OBJECT)
                            && isRawTypeAccessible(
                                injectSiteType, generatedTypeName.getPackageName())
                        ? XCodeBlock.ofCast(
                            toXPoet(erasedTypeName(injectSiteType)), instanceCodeBlock)
                        : instanceCodeBlock;
                return XCodeBlock.of(
                    "%L;",
                    invoke(injectionSite, generatedTypeName, maybeCastedInstance, dependencyUsage));
              })
          .collect(toConcatenatedCodeBlock());
    }

    /**
     * Invokes the injection method for {@code injectionSite}, with the dependencies transformed
     * using the {@code dependencyUsage} function.
     */
    private static XCodeBlock invoke(
        InjectionSite injectionSite,
        XClassName generatedTypeName,
        XCodeBlock instanceCodeBlock,
        Function<DependencyRequest, XCodeBlock> dependencyUsage) {
      ImmutableList<XCodeBlock> arguments =
          ImmutableList.<XCodeBlock>builder()
              .add(instanceCodeBlock)
              .addAll(
                  injectionSite.dependencies().stream()
                      .map(dependencyUsage)
                      .collect(toImmutableList()))
              .build();
      XClassName enclosingClass = membersInjectorNameForType(injectionSite.enclosingTypeElement());
      String methodName = membersInjectorMethodName(injectionSite);
      return invokeMethod(methodName, arguments, enclosingClass, generatedTypeName);
    }
  }

  private static XCodeBlock invokeMethod(
      String methodName,
      ImmutableList<XCodeBlock> parameters,
      XClassName enclosingClass,
      XClassName requestingClass) {
    XCodeBlock parameterBlock = makeParametersCodeBlock(parameters);
    return enclosingClass.equals(requestingClass)
        ? XCodeBlock.of("%L(%L)", methodName, parameterBlock)
        : XCodeBlock.of("%T.%L(%L)", enclosingClass, methodName, parameterBlock);
  }

  static XCodeBlock copyParameters(
      XFunSpecs.Builder methodBuilder,
      UniqueNameSet parameterNameSet,
      List<? extends XVariableElement> parameters,
      CompilerOptions compilerOptions) {
    return parameters.stream()
        .map(
            parameter -> {
              String name =
                  parameterNameSet.getUniqueName(
                      isMethodParameter(parameter)
                          ? asMethodParameter(parameter).getJvmName()
                          : getSimpleName(parameter));
              boolean isTypeNameAccessible = isRawTypePubliclyAccessible(parameter.getType());
              return copyParameter(
                  methodBuilder,
                  name,
                  parameter.getType().asTypeName(),
                  Nullability.of(parameter),
                  isTypeNameAccessible,
                  compilerOptions);
            })
        .collect(toParametersCodeBlock());
  }

  /**
   * Adds the parameter to the given {@code methodBuilder} and returns a code block that can be used
   * to call the parameter.
   *
   * <p>If the given {@code typeName} is not accessible, the {@link Object} type is used as the
   * parameter type instead, and the code block will contain a cast to the {@code typeName}.
   */
  static XCodeBlock copyParameter(
      XFunSpecs.Builder methodBuilder,
      String name,
      XTypeName typeName,
      Nullability nullability,
      boolean isTypeNameAccessible,
      CompilerOptions compilerOptions) {
    return copyParameterInternal(
        methodBuilder,
        name,
        typeName,
        isTypeNameAccessible,
        XTypeName.ANY_OBJECT,
        nullability,
        compilerOptions);
  }

  /**
   * Adds the framework parameter to the given {@code methodBuilder} and returns a code block that
   * can be used to call the parameter.
   *
   * <p>If the given {@code typeName} is not accessible, the unbounded framework type, e.g. {@code
   * Provider<?>} is used as the parameter type instead, and the code block will contain a cast to
   * the {@code typeName}.
   */
  static XCodeBlock copyFrameworkParameter(
      XFunSpecs.Builder methodBuilder,
      String name,
      XTypeName typeName,
      Nullability nullability,
      boolean isTypeNameAccessible,
      CompilerOptions compilerOptions) {
    return copyParameterInternal(
        methodBuilder,
        name,
        typeName,
        isTypeNameAccessible,
        asClassName(typeName.getRawTypeName()).parametrizedBy(XTypeName.ANY_WILDCARD),
        nullability,
        compilerOptions);
  }

  private static XCodeBlock copyParameterInternal(
      XFunSpecs.Builder methodBuilder,
      String name,
      XTypeName typeName,
      boolean isTypeNameAccessible,
      XTypeName accessibleTypeName,
      Nullability nullability,
      CompilerOptions compilerOptions) {
    methodBuilder.addParameter(
        XParameterSpecs.of(
            name,
            isTypeNameAccessible ? typeName : accessibleTypeName,
            nullability,
            compilerOptions));
    return isTypeNameAccessible
        ? XCodeBlock.of("%L", name)
        : XCodeBlock.ofCast(typeName, XCodeBlock.of("%L", name));
  }
}
