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

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedParameter;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.memberInjectedFieldSignatureForVariable;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.toConcatenatedCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.Preconditions;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.Nullability;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.extension.DaggerCollectors;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XAnnotations;
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
    // These names are already defined in factories and shouldn't be used for the proxy method name.
    private static final ImmutableSet<String> BANNED_PROXY_NAMES = ImmutableSet.of("get", "create");

    /**
     * Returns a method that invokes the binding's {@linkplain ProvisionBinding#bindingElement()
     * constructor} and injects the instance's members.
     */
    static MethodSpec create(ProvisionBinding binding, CompilerOptions compilerOptions) {
      XExecutableElement executableElement = asExecutable(binding.bindingElement().get());
      if (isConstructor(executableElement)) {
        return constructorProxy(asConstructor(executableElement));
      } else if (isMethod(executableElement)) {
        XMethodElement method = asMethod(executableElement);
        String methodName =
            BANNED_PROXY_NAMES.contains(getSimpleName(method))
                ? "proxy" + LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(method))
                : getSimpleName(method);
        return methodProxy(
            method,
            methodName,
            InstanceCastPolicy.IGNORE,
            CheckNotNullPolicy.get(binding, compilerOptions));
      }
      throw new AssertionError(executableElement);
    }

    /**
     * Invokes the injection method for {@code binding}, with the dependencies transformed with the
     * {@code dependencyUsage} function.
     */
    static CodeBlock invoke(
        ProvisionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<XExecutableParameterElement, String> uniqueAssistedParameterName,
        ClassName requestingClass,
        Optional<CodeBlock> moduleReference,
        CompilerOptions compilerOptions) {
      ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();
      moduleReference.ifPresent(arguments::add);
      invokeArguments(binding, dependencyUsage, uniqueAssistedParameterName)
          .forEach(arguments::add);

      ClassName enclosingClass = generatedClassNameForBinding(binding);
      MethodSpec methodSpec = create(binding, compilerOptions);
      return invokeMethod(methodSpec, arguments.build(), enclosingClass, requestingClass);
    }

    static ImmutableList<CodeBlock> invokeArguments(
        ProvisionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<XExecutableParameterElement, String> uniqueAssistedParameterName) {
      ImmutableMap<XExecutableParameterElement, DependencyRequest> dependencyRequestMap =
          binding.provisionDependencies().stream()
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

    private static MethodSpec constructorProxy(XConstructorElement constructor) {
      XTypeElement enclosingType = constructor.getEnclosingElement();
      MethodSpec.Builder builder =
          methodBuilder("newInstance")
              .addModifiers(PUBLIC, STATIC)
              .varargs(constructor.isVarArgs())
              .returns(enclosingType.getType().getTypeName())
              .addTypeVariables(typeVariableNames(enclosingType));

      copyThrows(builder, constructor);

      CodeBlock arguments =
          copyParameters(builder, new UniqueNameSet(), constructor.getParameters());
      return builder
          .addStatement("return new $T($L)", enclosingType.getType().getTypeName(), arguments)
          .build();
    }

    /**
     * Returns {@code true} if injecting an instance of {@code binding} from {@code callingPackage}
     * requires the use of an injection method.
     */
    static boolean requiresInjectionMethod(
        ProvisionBinding binding, CompilerOptions compilerOptions, ClassName requestingClass) {
      XExecutableElement executableElement = asExecutable(binding.bindingElement().get());
      return !binding.injectionSites().isEmpty()
          || binding.shouldCheckForNull(compilerOptions)
          || !isElementAccessibleFrom(executableElement, requestingClass.packageName())
          // This check should be removable once we drop support for -source 7
          || executableElement.getParameters().stream()
              .map(XExecutableParameterElement::getType)
              .anyMatch(type -> !isRawTypeAccessible(type, requestingClass.packageName()));
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
     * When a type has an inaccessible member from a supertype (e.g. an @Inject field in a parent
     * that's in a different package), a method in the supertype's package must be generated to give
     * the subclass's members injector a way to inject it. Each potentially inaccessible member
     * receives its own method, as the subclass may need to inject them in a different order from
     * the parent class.
     */
    static MethodSpec create(InjectionSite injectionSite) {
      String methodName = methodName(injectionSite);
      switch (injectionSite.kind()) {
        case METHOD:
          return methodProxy(
              asMethod(injectionSite.element()),
              methodName,
              InstanceCastPolicy.CAST_IF_NOT_PUBLIC,
              CheckNotNullPolicy.IGNORE);
        case FIELD:
          Optional<XAnnotation> qualifier =
              injectionSite.dependencies().stream()
                  // methods for fields have a single dependency request
                  .collect(DaggerCollectors.onlyElement())
                  .key()
                  .qualifier()
                  .map(DaggerAnnotation::xprocessing);
          return fieldProxy(asField(injectionSite.element()), methodName, qualifier);
      }
      throw new AssertionError(injectionSite);
    }

    /**
     * Invokes each of the injection methods for {@code injectionSites}, with the dependencies
     * transformed using the {@code dependencyUsage} function.
     *
     * @param instanceType the type of the {@code instance} parameter
     */
    static CodeBlock invokeAll(
        ImmutableSet<InjectionSite> injectionSites,
        ClassName generatedTypeName,
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
                            && isRawTypeAccessible(injectSiteType, generatedTypeName.packageName())
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
        ClassName generatedTypeName,
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
      ClassName enclosingClass = membersInjectorNameForType(injectionSite.enclosingTypeElement());
      MethodSpec methodSpec = create(injectionSite);
      return invokeMethod(methodSpec, arguments, enclosingClass, generatedTypeName);
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
    private static String methodName(InjectionSite injectionSite) {
      int index = injectionSite.indexAmongAtInjectMembersWithSameSimpleName();
      String indexString = index == 0 ? "" : String.valueOf(index + 1);
      return "inject"
          + LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(injectionSite.element()))
          + indexString;
    }
  }

  private enum InstanceCastPolicy {
    CAST_IF_NOT_PUBLIC, IGNORE;

    boolean useObjectType(XType instanceType) {
      return this == CAST_IF_NOT_PUBLIC && !isRawTypePubliclyAccessible(instanceType);
    }
  }

  private enum CheckNotNullPolicy {
    IGNORE, CHECK_FOR_NULL;

    CodeBlock checkForNull(CodeBlock maybeNull) {
      return this.equals(IGNORE)
          ? maybeNull
          : CodeBlock.of("$T.checkNotNullFromProvides($L)", Preconditions.class, maybeNull);
    }

    static CheckNotNullPolicy get(ProvisionBinding binding, CompilerOptions compilerOptions) {
      return binding.shouldCheckForNull(compilerOptions) ? CHECK_FOR_NULL : IGNORE;
    }
  }

  private static MethodSpec methodProxy(
      XMethodElement method,
      String methodName,
      InstanceCastPolicy instanceCastPolicy,
      CheckNotNullPolicy checkNotNullPolicy) {
    XTypeElement enclosingType = asTypeElement(method.getEnclosingElement());

    MethodSpec.Builder builder =
        methodBuilder(methodName)
            .addModifiers(PUBLIC, STATIC)
            .varargs(method.isVarArgs())
            .addTypeVariables(method.getExecutableType().getTypeVariableNames());

    UniqueNameSet parameterNameSet = new UniqueNameSet();
    CodeBlock instance;
    if (method.isStatic() || enclosingType.isCompanionObject()) {
      instance = CodeBlock.of("$T", rawTypeName(enclosingType.getType().getTypeName()));
    } else if (enclosingType.isKotlinObject()) {
      // Call through the singleton instance.
      // See: https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#static-methods
      instance = CodeBlock.of("$T.INSTANCE", rawTypeName(enclosingType.getType().getTypeName()));
    } else {
      builder.addTypeVariables(typeVariableNames(enclosingType));
      boolean useObject = instanceCastPolicy.useObjectType(enclosingType.getType());
      instance = copyInstance(builder, parameterNameSet, enclosingType.getType(), useObject);
    }
    CodeBlock arguments = copyParameters(builder, parameterNameSet, method.getParameters());
    CodeBlock invocation =
        checkNotNullPolicy.checkForNull(
            CodeBlock.of("$L.$L($L)", instance, method.getJvmName(), arguments));

    copyThrows(builder, method);

    if (isVoid(method.getReturnType())) {
      return builder.addStatement("$L", invocation).build();
    } else {
      Nullability.of(method)
          .nullableAnnotations()
          .forEach(builder::addAnnotation);
      return builder
          .returns(method.getReturnType().getTypeName())
          .addStatement("return $L", invocation)
          .build();
    }
  }

  private static MethodSpec fieldProxy(
      XFieldElement field, String methodName, Optional<XAnnotation> qualifier) {
    XTypeElement enclosingType = asTypeElement(field.getEnclosingElement());

    MethodSpec.Builder builder =
        methodBuilder(methodName)
            .addModifiers(PUBLIC, STATIC)
            .addAnnotation(
                AnnotationSpec.builder(TypeNames.INJECTED_FIELD_SIGNATURE)
                    .addMember("value", "$S", memberInjectedFieldSignatureForVariable(field))
                    .build())
            .addTypeVariables(typeVariableNames(enclosingType));

    qualifier.map(XAnnotations::getAnnotationSpec).ifPresent(builder::addAnnotation);

    boolean useObject = !isRawTypePubliclyAccessible(enclosingType.getType());
    UniqueNameSet parameterNameSet = new UniqueNameSet();
    CodeBlock instance =
        copyInstance(builder, parameterNameSet, enclosingType.getType(), useObject);
    CodeBlock argument = copyParameters(builder, parameterNameSet, ImmutableList.of(field));
    return builder.addStatement("$L.$L = $L", instance, getSimpleName(field), argument).build();
  }

  private static CodeBlock invokeMethod(
      MethodSpec methodSpec,
      ImmutableList<CodeBlock> parameters,
      ClassName enclosingClass,
      ClassName requestingClass) {
    checkArgument(methodSpec.parameters.size() == parameters.size());
    CodeBlock parameterBlock = makeParametersCodeBlock(parameters);
    return enclosingClass.equals(requestingClass)
        ? CodeBlock.of("$L($L)", methodSpec.name, parameterBlock)
        : CodeBlock.of("$T.$L($L)", enclosingClass, methodSpec.name, parameterBlock);
  }

  private static void copyThrows(MethodSpec.Builder methodBuilder, XExecutableElement method) {
    method.getThrownTypes().stream().map(XType::getTypeName).forEach(methodBuilder::addException);
  }

  private static CodeBlock copyParameters(
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
              return copyParameter(methodBuilder, parameter.getType(), name, useObject);
            })
        .collect(toParametersCodeBlock());
  }

  private static CodeBlock copyParameter(
      MethodSpec.Builder methodBuilder, XType type, String name, boolean useObject) {
    TypeName typeName = useObject ? TypeName.OBJECT : type.getTypeName();
    methodBuilder.addParameter(ParameterSpec.builder(typeName, name).build());
    return useObject ? CodeBlock.of("($T) $L", type.getTypeName(), name) : CodeBlock.of("$L", name);
  }

  private static CodeBlock copyInstance(
      MethodSpec.Builder methodBuilder,
      UniqueNameSet parameterNameSet,
      XType type,
      boolean useObject) {
    CodeBlock instance =
        copyParameter(methodBuilder, type, parameterNameSet.getUniqueName("instance"), useObject);
    // If we had to cast the instance add an extra parenthesis incase we're calling a method on it.
    return useObject ? CodeBlock.of("($L)", instance) : instance;
  }
}
