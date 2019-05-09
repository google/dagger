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

package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.FactoryGenerator.checkNotNullProvidesMethod;
import static dagger.internal.codegen.RequestKinds.requestTypeName;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.javapoet.CodeBlocks.toConcatenatedCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Convenience methods for creating and invoking {@link InjectionMethod}s. */
final class InjectionMethods {

  /**
   * A method that returns an object from a {@code @Provides} method or an {@code @Inject}ed
   * constructor. Its parameters match the dependency requests for constructor and members
   * injection.
   *
   * <p>For {@code @Provides} methods named "foo", the method name is "proxyFoo". If the
   * {@code @Provides} method and its raw parameter types are publicly accessible, no method is
   * necessary and this method returns {@link Optional#empty()}.
   *
   * <p>Example:
   *
   * <pre><code>
   * abstract class FooModule {
   *   {@literal @Provides} static Foo provideFoo(Bar bar, Baz baz) { … }
   * }
   *
   * public static proxyProvideFoo(Bar bar, Baz baz) { … }
   * </code></pre>
   *
   * <p>For {@code @Inject}ed constructors, the method name is "newFoo". If the constructor and its
   * raw parameter types are publicly accessible, no method is necessary and this method returns
   * {@code Optional#empty()}.
   *
   * <p>Example:
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
     * Names of methods that are already defined in factories and shouldn't be used for the proxy
     * method name.
     */
    private static final ImmutableSet<String> BANNED_PROXY_NAMES = ImmutableSet.of("get", "create");

    /**
     * Returns a method that invokes the binding's {@linkplain ProvisionBinding#bindingElement()
     * constructor} and injects the instance's members.
     */
    static InjectionMethod create(
        ProvisionBinding binding, CompilerOptions compilerOptions, DaggerElements elements) {
      ClassName proxyEnclosingClass = generatedClassNameForBinding(binding);
      ExecutableElement element = MoreElements.asExecutable(binding.bindingElement().get());
      switch (element.getKind()) {
        case CONSTRUCTOR:
          return constructorProxy(proxyEnclosingClass, element, elements);
        case METHOD:
          return methodProxy(
              proxyEnclosingClass,
              element,
              methodName(element),
              ReceiverAccessibility.IGNORE,
              CheckNotNullPolicy.get(binding, compilerOptions),
              elements);
        default:
          throw new AssertionError(element);
      }
    }

    /**
     * Invokes the injection method for {@code binding}, with the dependencies transformed with the
     * {@code dependencyUsage} function.
     */
    // TODO(ronshapiro): Further extract a ProvisionMethod type that composes an InjectionMethod, so
    // users can write ProvisionMethod.create().invoke()
    static CodeBlock invoke(
        ProvisionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        ClassName requestingClass,
        Optional<CodeBlock> moduleReference,
        CompilerOptions compilerOptions,
        DaggerElements elements) {
      ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();
      moduleReference.ifPresent(arguments::add);
      arguments.addAll(
          injectionMethodArguments(
              binding.provisionDependencies(), dependencyUsage, requestingClass));
      // TODO(ronshapiro): make InjectionMethods @Injectable
      return create(binding, compilerOptions, elements).invoke(arguments.build(), requestingClass);
    }

    private static InjectionMethod constructorProxy(
        ClassName proxyEnclosingClass, ExecutableElement constructor, DaggerElements elements) {
      TypeElement enclosingType = MoreElements.asType(constructor.getEnclosingElement());
      InjectionMethod.Builder injectionMethod =
          InjectionMethod.builder(elements)
              .name(methodName(constructor))
              .returnType(enclosingType.asType())
              .enclosingClass(proxyEnclosingClass);

      injectionMethod
          .copyTypeParameters(enclosingType)
          .copyThrows(constructor);

      CodeBlock arguments = injectionMethod.copyParameters(constructor);
      injectionMethod
          .methodBodyBuilder()
          .addStatement("return new $T($L)", enclosingType, arguments);
      return injectionMethod.build();
    }

    /**
     * Returns {@code true} if injecting an instance of {@code binding} from {@code callingPackage}
     * requires the use of an injection method.
     */
    static boolean requiresInjectionMethod(
        ProvisionBinding binding,
        ImmutableList<Expression> arguments,
        CompilerOptions compilerOptions,
        String callingPackage,
        DaggerTypes types) {
      ExecutableElement method = MoreElements.asExecutable(binding.bindingElement().get());
      return !binding.injectionSites().isEmpty()
          || binding.shouldCheckForNull(compilerOptions)
          || !isElementAccessibleFrom(method, callingPackage)
          || !areParametersAssignable(method, arguments, types)
          // This check should be removable once we drop support for -source 7
          || method.getParameters().stream()
              .map(VariableElement::asType)
              .anyMatch(type -> !isRawTypeAccessible(type, callingPackage));
    }

    private static boolean areParametersAssignable(
        ExecutableElement element, ImmutableList<Expression> arguments, DaggerTypes types) {
      List<? extends VariableElement> parameters = element.getParameters();
      checkArgument(parameters.size() == arguments.size());
      for (int i = 0; i < parameters.size(); i++) {
        if (!types.isAssignable(arguments.get(i).type(), parameters.get(i).asType())) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns the name of the {@code static} method that wraps {@code method}. For methods that are
     * associated with {@code @Inject} constructors, the method will also inject all {@link
     * InjectionSite}s.
     */
    private static String methodName(ExecutableElement method) {
      switch (method.getKind()) {
        case CONSTRUCTOR:
          return "newInstance";
        case METHOD:
          String methodName = method.getSimpleName().toString();
          return BANNED_PROXY_NAMES.contains(methodName)
              ? "proxy" + LOWER_CAMEL.to(UPPER_CAMEL, methodName)
              : methodName;
        default:
          throw new AssertionError(method);
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
     * When a type has an inaccessible member from a supertype (e.g. an @Inject field in a parent
     * that's in a different package), a method in the supertype's package must be generated to give
     * the subclass's members injector a way to inject it. Each potentially inaccessible member
     * receives its own method, as the subclass may need to inject them in a different order from
     * the parent class.
     */
    static InjectionMethod create(InjectionSite injectionSite, DaggerElements elements) {
      String methodName = methodName(injectionSite);
      ClassName proxyEnclosingClass = membersInjectorNameForType(
          MoreElements.asType(injectionSite.element().getEnclosingElement()));
      switch (injectionSite.kind()) {
        case METHOD:
          return methodProxy(
              proxyEnclosingClass,
              MoreElements.asExecutable(injectionSite.element()),
              methodName,
              ReceiverAccessibility.CAST_IF_NOT_PUBLIC,
              CheckNotNullPolicy.IGNORE,
              elements);
        case FIELD:
          return fieldProxy(
              proxyEnclosingClass,
              MoreElements.asVariable(injectionSite.element()),
              methodName,
              elements);
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
        TypeMirror instanceType,
        DaggerTypes types,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        DaggerElements elements) {
      return injectionSites
          .stream()
          .map(
              injectionSite -> {
                TypeMirror injectSiteType =
                    types.erasure(injectionSite.element().getEnclosingElement().asType());

                // If instance has been declared as Object because it is not accessible from the
                // component, but the injectionSite is in a supertype of instanceType that is
                // publicly accessible, the InjectionSiteMethod will request the actual type and not
                // Object as the first parameter. If so, cast to the supertype which is accessible
                // from within generatedTypeName
                CodeBlock maybeCastedInstance =
                    !types.isSubtype(instanceType, injectSiteType)
                            && isTypeAccessibleFrom(injectSiteType, generatedTypeName.packageName())
                        ? CodeBlock.of("($T) $L", injectSiteType, instanceCodeBlock)
                        : instanceCodeBlock;
                return CodeBlock.of(
                    "$L;",
                    invoke(
                        injectionSite,
                        generatedTypeName,
                        maybeCastedInstance,
                        dependencyUsage,
                        elements));
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
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        DaggerElements elements) {
      List<CodeBlock> arguments = new ArrayList<>();
      arguments.add(instanceCodeBlock);
      if (!injectionSite.dependencies().isEmpty()) {
        arguments.addAll(
            injectionSite
                .dependencies()
                .stream()
                .map(dependencyUsage)
                .collect(toList()));
      }
      return create(injectionSite, elements).invoke(arguments, generatedTypeName);
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
          + LOWER_CAMEL.to(UPPER_CAMEL, injectionSite.element().getSimpleName().toString())
          + indexString;
    }
  }

  /**
   * Returns an argument list suitable for calling an injection method. Down-casts any arguments
   * that are {@code Object} (or {@code Provider<Object>}) at the caller but not the method.
   *
   * @param dependencies the dependencies used by the method
   * @param dependencyUsage function to apply on each of {@code dependencies} before casting
   * @param requestingClass the class calling the injection method
   */
  private static ImmutableList<CodeBlock> injectionMethodArguments(
      ImmutableSet<DependencyRequest> dependencies,
      Function<DependencyRequest, CodeBlock> dependencyUsage,
      ClassName requestingClass) {
    return dependencies.stream()
        .map(dep -> injectionMethodArgument(dep, dependencyUsage.apply(dep), requestingClass))
        .collect(toImmutableList());
  }

  private static CodeBlock injectionMethodArgument(
      DependencyRequest dependency, CodeBlock argument, ClassName generatedTypeName) {
    TypeMirror keyType = dependency.key().type();
    CodeBlock.Builder codeBlock = CodeBlock.builder();
    if (!isRawTypeAccessible(keyType, generatedTypeName.packageName())
        && isTypeAccessibleFrom(keyType, generatedTypeName.packageName())) {
      if (!dependency.kind().equals(RequestKind.INSTANCE)) {
        TypeName usageTypeName = accessibleType(dependency);
        codeBlock.add("($T) ($T)", usageTypeName, rawTypeName(usageTypeName));
      } else if (dependency.requestElement().get().asType().getKind().equals(TypeKind.TYPEVAR)) {
        codeBlock.add("($T)", keyType);
      }
    }
    return codeBlock.add(argument).build();
  }

  /**
   * Returns the parameter type for {@code dependency}. If the raw type is not accessible, returns
   * {@link Object}.
   */
  private static TypeName accessibleType(DependencyRequest dependency) {
    TypeName typeName = requestTypeName(dependency.kind(), accessibleType(dependency.key().type()));
    return dependency
            .requestElement()
            .map(element -> element.asType().getKind().isPrimitive())
            .orElse(false)
        ? typeName.unbox()
        : typeName;
  }

  /**
   * Returns the accessible type for {@code type}. If the raw type is not accessible, returns {@link
   * Object}.
   */
  private static TypeName accessibleType(TypeMirror type) {
    return isRawTypePubliclyAccessible(type) ? TypeName.get(type) : TypeName.OBJECT;
  }

  /**
   * Returns the accessible type for {@code type}. If the raw type is not accessible, returns {@link
   * Object}.
   */
  // TODO(ronshapiro): Can we use DaggerTypes.publiclyAccessibleType in place of this method?
  private static TypeMirror accessibleType(TypeMirror type, DaggerElements elements) {
    return isRawTypePubliclyAccessible(type)
        ? type
        : elements.getTypeElement(Object.class).asType();
  }

  private enum ReceiverAccessibility {
    CAST_IF_NOT_PUBLIC {
      @Override
      TypeMirror parameterType(TypeMirror type, DaggerElements elements) {
        return accessibleType(type, elements);
      }

      @Override
      CodeBlock potentiallyCast(CodeBlock instance, TypeMirror instanceType) {
        return instanceWithPotentialCast(instance, instanceType);
      }
    },
    IGNORE {
      @Override
      TypeMirror parameterType(TypeMirror type, DaggerElements elements) {
        return type;
      }

      @Override
      CodeBlock potentiallyCast(CodeBlock instance, TypeMirror instanceType) {
        return instance;
      }
    },
    ;

    abstract TypeMirror parameterType(TypeMirror type, DaggerElements elements);
    abstract CodeBlock potentiallyCast(CodeBlock instance, TypeMirror instanceType);
  }

  private static CodeBlock instanceWithPotentialCast(CodeBlock instance, TypeMirror instanceType) {
    return isRawTypePubliclyAccessible(instanceType)
        ? instance
        : CodeBlock.of("(($T) $L)", instanceType, instance);
  }

  private enum CheckNotNullPolicy {
    IGNORE, CHECK_FOR_NULL;
    CodeBlock checkForNull(CodeBlock maybeNull) {
      if (this.equals(IGNORE)) {
        return maybeNull;
      }
      return checkNotNullProvidesMethod(maybeNull);
    }

    static CheckNotNullPolicy get(ProvisionBinding binding, CompilerOptions compilerOptions) {
      return binding.shouldCheckForNull(compilerOptions) ? CHECK_FOR_NULL : IGNORE;
    }
  }

  private static InjectionMethod methodProxy(
      ClassName proxyEnclosingClass,
      ExecutableElement method,
      String methodName,
      ReceiverAccessibility receiverAccessibility,
      CheckNotNullPolicy checkNotNullPolicy,
      DaggerElements elements) {
    TypeElement enclosingType = MoreElements.asType(method.getEnclosingElement());
    InjectionMethod.Builder injectionMethod =
        InjectionMethod.builder(elements).name(methodName).enclosingClass(proxyEnclosingClass);
    ParameterSpec instance = null;
    if (!method.getModifiers().contains(STATIC)) {
      instance =
          injectionMethod.addParameter(
              "instance", receiverAccessibility.parameterType(enclosingType.asType(), elements));
    }

    CodeBlock arguments = injectionMethod.copyParameters(method);
    if (!method.getReturnType().getKind().equals(VOID)) {
      injectionMethod
          .returnType(method.getReturnType())
          .nullableAnnotation(getNullableType(method));
      injectionMethod.methodBodyBuilder().add("return ");
    }
    CodeBlock.Builder proxyInvocation = CodeBlock.builder();
    if (method.getModifiers().contains(STATIC)) {
      proxyInvocation.add("$T", rawTypeName(TypeName.get(enclosingType.asType())));
    } else {
      injectionMethod.copyTypeParameters(enclosingType);
      proxyInvocation.add(
          receiverAccessibility.potentiallyCast(
              CodeBlock.of("$N", instance), enclosingType.asType()));
    }

    injectionMethod
        .copyTypeParameters(method)
        .copyThrows(method);

    proxyInvocation.add(".$N($L)", method.getSimpleName(), arguments);
    injectionMethod
        .methodBodyBuilder()
        .add(checkNotNullPolicy.checkForNull(proxyInvocation.build()))
        .add(";\n");
    return injectionMethod.build();
  }

  private static InjectionMethod fieldProxy(
      ClassName proxyEnclosingClass,
      VariableElement field,
      String methodName,
      DaggerElements elements) {
    TypeElement enclosingType = MoreElements.asType(field.getEnclosingElement());
    InjectionMethod.Builder injectionMethod =
        InjectionMethod.builder(elements).name(methodName).enclosingClass(proxyEnclosingClass);
    injectionMethod.copyTypeParameters(enclosingType);

    ParameterSpec instance =
        injectionMethod.addParameter("instance", accessibleType(enclosingType.asType(), elements));
    CodeBlock parameter = injectionMethod.copyParameter(field);
    injectionMethod
        .methodBodyBuilder()
        .addStatement(
            "$L.$L = $L",
            instanceWithPotentialCast(CodeBlock.of("$N", instance), enclosingType.asType()),
            field.getSimpleName(),
            parameter);
    return injectionMethod.build();
  }
}
