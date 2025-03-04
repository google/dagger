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

import static androidx.room.compiler.codegen.XTypeNameKt.toJavaPoet;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedParameter;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.generatedProxyMethodName;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorMethodName;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.toConcatenatedCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.Nullability;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Convenience methods for creating and invoking {@link InjectionMethod}s. */
final class InjectionMethods {

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
    static CodeBlock invoke(
        ContributionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<XExecutableParameterElement, String> uniqueAssistedParameterName,
        XClassName requestingClass,
        Optional<CodeBlock> moduleReference,
        CompilerOptions compilerOptions) {
      ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();
      moduleReference.ifPresent(arguments::add);
      invokeArguments(binding, dependencyUsage, uniqueAssistedParameterName)
          .forEach(arguments::add);

      XClassName enclosingClass = generatedClassNameForBinding(binding);
      String methodName = generatedProxyMethodName(binding);
      return invokeMethod(methodName, arguments.build(), enclosingClass, requestingClass);
    }

    static ImmutableList<CodeBlock> invokeArguments(
        ContributionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<XExecutableParameterElement, String> uniqueAssistedParameterName) {
      ImmutableMap<XExecutableParameterElement, DependencyRequest> dependencyRequestMap =
          provisionDependencies(binding).stream()
              .collect(
                  toImmutableMap(
                      request -> asMethodParameter(request.requestElement().get().xprocessing()),
                      request -> request));

      ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();
      XExecutableElement method = asExecutable(binding.bindingElement().get());
      for (XExecutableParameterElement parameter : method.getParameters()) {
        if (isAssistedParameter(parameter)) {
          arguments.add(CodeBlock.of("$L", uniqueAssistedParameterName.apply(parameter)));
        } else if (dependencyRequestMap.containsKey(parameter)) {
          DependencyRequest request = dependencyRequestMap.get(parameter);
          arguments.add(dependencyUsage.apply(request));
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
    static CodeBlock invokeAll(
        ImmutableSet<InjectionSite> injectionSites,
        XClassName generatedTypeName,
        CodeBlock instanceCodeBlock,
        XType instanceType,
        Function<DependencyRequest, CodeBlock> dependencyUsage) {
      return injectionSites.stream()
          .map(
              injectionSite -> {
                XType injectSiteType = injectionSite.enclosingTypeElement().getType();

                // If instance has been declared as Object because it is not accessible from the
                // component, but the injectionSite is in a supertype of instanceType that is
                // publicly accessible, the InjectionSiteMethod will request the actual type and not
                // Object as the first parameter. If so, cast to the supertype which is accessible
                // from within generatedTypeName
                CodeBlock maybeCastedInstance =
                    instanceType.getTypeName().equals(TypeName.OBJECT)
                            && isRawTypeAccessible(
                                injectSiteType, generatedTypeName.getPackageName())
                        ? CodeBlock.of("($T) $L", erasedTypeName(injectSiteType), instanceCodeBlock)
                        : instanceCodeBlock;
                return CodeBlock.of(
                    "$L;",
                    invoke(injectionSite, generatedTypeName, maybeCastedInstance, dependencyUsage));
              })
          .collect(toConcatenatedCodeBlock());
    }

    /**
     * Invokes the injection method for {@code injectionSite}, with the dependencies transformed
     * using the {@code dependencyUsage} function.
     */
    private static CodeBlock invoke(
        InjectionSite injectionSite,
        XClassName generatedTypeName,
        CodeBlock instanceCodeBlock,
        Function<DependencyRequest, CodeBlock> dependencyUsage) {
      ImmutableList<CodeBlock> arguments =
          ImmutableList.<CodeBlock>builder()
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

  private static CodeBlock invokeMethod(
      String methodName,
      ImmutableList<CodeBlock> parameters,
      XClassName enclosingClass,
      XClassName requestingClass) {
    CodeBlock parameterBlock = makeParametersCodeBlock(parameters);
    return enclosingClass.equals(requestingClass)
        ? CodeBlock.of("$L($L)", methodName, parameterBlock)
        : CodeBlock.of("$T.$L($L)", toJavaPoet(enclosingClass), methodName, parameterBlock);
  }

  static CodeBlock copyParameters(
      MethodSpec.Builder methodBuilder,
      UniqueNameSet parameterNameSet,
      List<? extends XVariableElement> parameters) {
    return parameters.stream()
        .map(
            parameter -> {
              String name =
                  parameterNameSet.getUniqueName(
                      isMethodParameter(parameter)
                          ? asMethodParameter(parameter).getJvmName()
                          : getSimpleName(parameter));
              boolean useObject = !isRawTypePubliclyAccessible(parameter.getType());
              return copyParameter(
                  methodBuilder, parameter.getType(), name, useObject, Nullability.of(parameter));
            })
        .collect(toParametersCodeBlock());
  }

  static CodeBlock copyParameter(
      MethodSpec.Builder methodBuilder,
      XType type,
      String name,
      boolean useObject,
      Nullability nullability) {
    TypeName typeName = useObject ? TypeName.OBJECT : type.getTypeName();
    nullability.typeUseNullableAnnotations().stream()
        .map(XConverters::toJavaPoet)
        .map(it -> AnnotationSpec.builder(it).build())
        .forEach(typeName::annotated);
    methodBuilder.addParameter(
        ParameterSpec.builder(typeName, name)
            .addAnnotations(
                nullability.nonTypeUseNullableAnnotations().stream()
                    .map(XConverters::toJavaPoet)
                    .map(it -> AnnotationSpec.builder(it).build())
                    .collect(toImmutableList()))
            .build());
    return useObject ? CodeBlock.of("($T) $L", type.getTypeName(), name) : CodeBlock.of("$L", name);
  }
}
