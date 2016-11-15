/*
 * Copyright (C) 2016 The Dagger Authors.
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
import static com.google.common.collect.Iterables.toArray;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.Accessibility.isElementPubliclyAccessible;
import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.Accessibility.isTypePubliclyAccessible;
import static dagger.internal.codegen.CodeBlocks.javadocLinkTo;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.TypeNames.rawTypeName;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Proxy methods ("proxies") are generated methods used to give component implementations access to
 * {@link Element}s that are inaccessible as written in the source code. For example, a component
 * cannot directly invoke a package-private {@code @Inject} constructor in a different package.
 *
 * <p>Since proxies are generated separately from their usages, they cannot make any assumptions
 * about the types or packages from which methods will be invoked. Thus, any type or element that is
 * not public is considered to be "inaccessible".
 *
 * <p>This class generates proxies for any {@link ExecutableElement}, but the form of the methods
 * are somewhat tailored to how they are used within components.
 *
 * <p>Proxies have the following attributes:
 *
 * <ul>
 *   <li>Proxies are always {@code public}, {@code static} methods.
 *   <li>The return type of the proxy is always the return type of the method or the constructed
 *       type regardless of its accessibility. For example, if a proxied method returns {@code
 *       MyPackagePrivateClass}, the proxy method will also return {@code MyPackagePrivateClass}
 *       because the accessibility of the return type does not impact callers.
 *   <li>Proxies for constructors are named "{@code newTypeName}" (where "{@code TypeName}" is the
 *       name of the type being constructed) and proxies for methods are named "{@code
 *       proxyMethodName}" (where "{@code methodName}" is the name of the method being proxied).
 *   <li>If the element being proxied is an instance method, the first parameter will be the
 *       instance.
 *   <li>The rest of the parameters of the proxy method are that of the proxied method unless the
 *       raw type of a parameter is inaccessible, in which case it is {@link Object}. Passing an
 *       object to this method that is not of the proxied parameter type will result in a {@link
 *       ClassCastException}.
 *       <p>While it is not required by the language that a method's parameter types be accessible
 *       to invoke it, components often hold references to {@link javax.inject.Provider} as raw
 *       types in order to dodge similar accessibility restrictions. This means that the {@code
 *       {@link javax.inject.Provider#get()}} method will return {@link Object}. Since it cannot be
 *       cast to the the more specific type on the calling side, we must accept {@link Object} in
 *       the proxy method.
 * </ul>
 *
 * <p>Proxies are not generated under the following conditions:
 *
 * <ul>
 *   <li>If an {@link ExecutableElement} is publicly accessible and all of its {@linkplain
 *       ExecutableElement#getParameters() parameters} are publicly accessible types, no proxy is
 *       necessary. If the type of a parameter has a type argument that is is inaccessible, but the
 *       raw type that is accessible, the type is considered to be accessible because callers can
 *       always hold references to the raw type.
 *   <li>If an {@link ExecutableElement} or any of its enclosing types are {@code private}, no proxy
 *       is generated because it is impossible to write Java (without reflection) that accesses the
 *       element.
 * </ul>
 */
final class Proxies {

  /**
   * Returns {@code true} if the given method has limited access, thus requiring a proxy for some
   * cases.
   */
  static boolean shouldGenerateProxy(ExecutableElement method) {
    return !isElementPubliclyAccessible(method)
        || method
            .getParameters()
            .stream()
            .map(VariableElement::asType)
            .anyMatch(type -> !isRawTypePubliclyAccessible(type));
  }

  /** Returns {@code true} if accessing the given method from the given package requires a proxy. */
  static boolean requiresProxyAccess(ExecutableElement method, String callingPackage) {
    return !isElementAccessibleFrom(method, callingPackage)
        || method
            .getParameters()
            .stream()
            .map(VariableElement::asType)
            .anyMatch(type -> !isRawTypeAccessible(type, callingPackage));
  }

  /** Returns the name of the method that proxies access to the given method. */
  static String proxyName(ExecutableElement method) {
    switch (method.getKind()) {
      case CONSTRUCTOR:
        return "new" + method.getEnclosingElement().getSimpleName();
      case METHOD:
        return "proxy" + LOWER_CAMEL.to(UPPER_CAMEL, method.getSimpleName().toString());
      case STATIC_INIT:
      case INSTANCE_INIT:
        throw new IllegalArgumentException(
            "cannot proxy initializers because they cannot be invoked directly: " + method);
      default:
        throw new AssertionError(method);
    }
  }

  /**
   * Returns a proxy method implementation for the method.
   *
   * @throws IllegalArgumentException if the method is publicly accessible
   */
  // TODO(gak): expand support to proxy fields
  static MethodSpec createProxy(ExecutableElement method) {
    checkArgument(
        shouldGenerateProxy(method),
        "method and all of its arguments are accessible; proxy isn't necessary: %s",
        method);
    final MethodSpec.Builder builder;
    switch (method.getKind()) {
      case CONSTRUCTOR:
        builder = forConstructor(method);
        break;
      case METHOD:
        builder = forMethod(method);
        break;
      default:
        throw new AssertionError();
    }
    builder.addJavadoc("Proxies $L.", javadocLinkTo(method));
    builder.addModifiers(PUBLIC, STATIC);

    copyTypeParameters(method, builder);
    copyThrows(method, builder);

    return builder.build();
  }

  private static MethodSpec.Builder forConstructor(ExecutableElement constructor) {
    TypeElement enclosingType = MoreElements.asType(constructor.getEnclosingElement());
    MethodSpec.Builder methodBuilder = methodBuilder(proxyName(constructor));

    copyTypeParameters(enclosingType, methodBuilder);

    methodBuilder.returns(TypeName.get(enclosingType.asType()));

    CodeBlock arguments =
        copyParameters(
            constructor, methodBuilder, new UniqueNameSet(), new ImmutableList.Builder<>());

    methodBuilder.addCode("return new $T($L);", enclosingType, arguments);

    return methodBuilder;
  }

  private static MethodSpec.Builder forMethod(ExecutableElement method) {
    TypeElement enclosingType = MoreElements.asType(method.getEnclosingElement());
    MethodSpec.Builder methodBuilder = methodBuilder(proxyName(method));

    UniqueNameSet nameSet = new UniqueNameSet();
    ImmutableList.Builder<CodeBlock> argumentsBuilder = new ImmutableList.Builder<>();
    if (!method.getModifiers().contains(STATIC)) {
      methodBuilder.addParameter(
          TypeName.get(enclosingType.asType()), nameSet.getUniqueName("instance"));
    }
    CodeBlock arguments = copyParameters(method, methodBuilder, nameSet, argumentsBuilder);
    if (!method.getReturnType().getKind().equals(VOID)) {
      methodBuilder.addCode("return ");
    }
    if (method.getModifiers().contains(STATIC)) {
      methodBuilder.addCode("$T", rawTypeName(TypeName.get(enclosingType.asType())));
    } else {
      copyTypeParameters(enclosingType, methodBuilder);
      // "instance" is guaranteed b/c it was the first name into the UniqueNameSet
      methodBuilder.addCode("instance", method.getSimpleName());
    }
    methodBuilder.addCode(".$N($L);", method.getSimpleName(), arguments);
    methodBuilder.returns(TypeName.get(method.getReturnType()));
    return methodBuilder;
  }

  private static void copyThrows(ExecutableElement method, MethodSpec.Builder methodBuilder) {
    for (TypeMirror thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }
  }

  private static CodeBlock copyParameters(
      ExecutableElement method,
      MethodSpec.Builder methodBuilder,
      UniqueNameSet nameSet,
      ImmutableList.Builder<CodeBlock> argumentsBuilder) {
    for (VariableElement parameter : method.getParameters()) {
      TypeMirror parameterType = parameter.asType();
      boolean useObject = !isTypePubliclyAccessible(parameterType);
      TypeName typeName = useObject ? TypeName.OBJECT : TypeName.get(parameterType);
      String name = nameSet.getUniqueName(parameter.getSimpleName().toString());
      argumentsBuilder.add(
          useObject ? CodeBlock.of("($T) $L", parameterType, name) : CodeBlock.of(name));
      ParameterSpec.Builder parameterBuilder =
          ParameterSpec.builder(typeName, name)
              .addModifiers(toArray(parameter.getModifiers(), Modifier.class));
      methodBuilder.addParameter(parameterBuilder.build());
    }
    methodBuilder.varargs(method.isVarArgs());
    return makeParametersCodeBlock(argumentsBuilder.build());
  }

  private static void copyTypeParameters(
      Parameterizable parameterizable, MethodSpec.Builder methodBuilder) {
    for (TypeParameterElement typeParameterElement : parameterizable.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParameterElement));
    }
  }

  private Proxies() {}
}
