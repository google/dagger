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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** The collection of error messages to be reported back to users. */
final class ErrorMessages {

  private static final UnaryOperator<String> PRODUCTION =
      s ->
          s.replace("component", "production component")
              .replace("Component", "ProductionComponent");

  private static final UnaryOperator<String> SUBCOMPONENT =
      s -> s.replace("component", "subcomponent").replace("Component", "Subcomponent");

  private static final UnaryOperator<String> FACTORY = s -> s.replace("Builder", "Factory");

  private static final ImmutableMap<ComponentKind, Function<String, String>>
      COMPONENT_TRANSFORMATIONS =
          ImmutableMap.of(
              ComponentKind.COMPONENT, UnaryOperator.identity(),
              ComponentKind.SUBCOMPONENT, SUBCOMPONENT,
              ComponentKind.PRODUCTION_COMPONENT, PRODUCTION,
              ComponentKind.PRODUCTION_SUBCOMPONENT, PRODUCTION.andThen(SUBCOMPONENT));

  static ComponentMessages componentMessagesFor(ComponentKind componentKind) {
    return new ComponentMessages(COMPONENT_TRANSFORMATIONS.get(componentKind));
  }

  static ComponentMessages componentMessagesFor(ComponentAnnotation componentAnnotation) {
    return new ComponentMessages(
        transformation(componentAnnotation.isProduction(), componentAnnotation.isSubcomponent()));
  }

  static ComponentCreatorMessages creatorMessagesFor(ComponentCreatorAnnotation creatorAnnotation) {
    Function<String, String> transformation =
        transformation(
            creatorAnnotation.isProductionCreatorAnnotation(),
            creatorAnnotation.isSubcomponentCreatorAnnotation());
    switch (creatorAnnotation.creatorKind()) {
      case BUILDER:
        return new BuilderMessages(transformation);
      case FACTORY:
        return new FactoryMessages(transformation);
    }
    throw new AssertionError(creatorAnnotation);
  }

  private static Function<String, String> transformation(
      boolean isProduction, boolean isSubcomponent) {
    Function<String, String> transformation = isProduction ? PRODUCTION : UnaryOperator.identity();
    return isSubcomponent ? transformation.andThen(SUBCOMPONENT) : transformation;
  }

  private abstract static class Messages {
    private final Function<String, String> transformation;

    Messages(Function<String, String> transformation) {
      this.transformation = transformation;
    }

    protected final String process(String s) {
      return transformation.apply(s);
    }
  }

  /** Errors for components. */
  static final class ComponentMessages extends Messages {
    ComponentMessages(Function<String, String> transformation) {
      super(transformation);
    }

    final String moreThanOne() {
      return process("@Component has more than one @Component.Builder or @Component.Factory: %s");
    }
  }

  /** Errors for component creators. */
  abstract static class ComponentCreatorMessages extends Messages {
    ComponentCreatorMessages(Function<String, String> transformation) {
      super(transformation);
    }

    static String builderMethodRequiresNoArgs() {
      return "Methods returning a @Component.Builder must have no arguments";
    }

    static String moreThanOneRefToSubcomponent() {
      return "Only one method can create a given subcomponent. %s is created by: %s";
    }

    final String invalidConstructor() {
      return process("@Component.Builder classes must have exactly one constructor,"
          + " and it must not be private or have any parameters");
    }

    final String generics() {
      return process("@Component.Builder types must not have any generic types");
    }

    final String mustBeInComponent() {
      return process("@Component.Builder types must be nested within a @Component");
    }

    final String mustBeClassOrInterface() {
      return process("@Component.Builder types must be abstract classes or interfaces");
    }

    final String isPrivate() {
      return process("@Component.Builder types must not be private");
    }

    final String mustBeStatic() {
      return process("@Component.Builder types must be static");
    }

    final String mustBeAbstract() {
      return process("@Component.Builder types must be abstract");
    }

    abstract String missingFactoryMethod();

    abstract String multipleSettersForModuleOrDependencyType();

    abstract String extraSetters();

    abstract String missingSetters();

    abstract String twoFactoryMethods();

    abstract String inheritedTwoFactoryMethods();

    abstract String factoryMethodMustReturnComponentType();

    final String inheritedFactoryMethodMustReturnComponentType() {
      return factoryMethodMustReturnComponentType() + ". Inherited method: %s";
    }

    abstract String factoryMethodMayNotBeAnnotatedWithBindsInstance();

    final String inheritedFactoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return factoryMethodMayNotBeAnnotatedWithBindsInstance() + ". Inherited method: %s";
    }

    final String setterMethodsMustTakeOneArg() {
      return process("@Component.Builder methods must not have more than one argument");
    }

    final String inheritedSetterMethodsMustTakeOneArg() {
      return setterMethodsMustTakeOneArg() + ". Inherited method: %s";
    }

    final String setterMethodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + " or a supertype of the builder");
    }

    final String inheritedSetterMethodsMustReturnVoidOrBuilder() {
      return setterMethodsMustReturnVoidOrBuilder() + ". Inherited method: %s";
    }

    final String methodsMayNotHaveTypeParameters() {
      return process("@Component.Builder methods must not have type parameters");
    }

    final String inheritedMethodsMayNotHaveTypeParameters() {
      return methodsMayNotHaveTypeParameters() + ". Inherited method: %s";
    }

    abstract String nonBindsInstanceParametersMayNotBePrimitives();

    final String inheritedNonBindsInstanceParametersMayNotBePrimitives() {
      return nonBindsInstanceParametersMayNotBePrimitives() + ". Inherited method: %s";
    }

    final String factoryMethodReturnsSupertypeWithMissingMethods(
        TypeElement component,
        TypeElement componentBuilder,
        TypeMirror returnType,
        ExecutableElement buildMethod,
        Set<ExecutableElement> additionalMethods) {
      return String.format(
          "%1$s.%2$s() returns %3$s, but %4$s declares additional component method(s): %5$s. In "
              + "order to provide type-safe access to these methods, override %2$s() to return "
              + "%4$s",
          componentBuilder.getQualifiedName(),
          buildMethod.getSimpleName(),
          returnType,
          component.getQualifiedName(),
          Joiner.on(", ").join(additionalMethods));
    }

    final String bindsInstanceNotAllowedOnBothSetterMethodAndParameter() {
      return process("@Component.Builder setter methods may not have @BindsInstance on both the "
          + "method and its parameter; choose one or the other");
    }

    final String inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter() {
      return bindsInstanceNotAllowedOnBothSetterMethodAndParameter() + ". Inherited method: %s";
    }
  }

  private static final class BuilderMessages extends ComponentCreatorMessages {
    BuilderMessages(Function<String, String> transformation) {
      super(transformation);
    }

    @Override
    String missingFactoryMethod() {
      return process(
          "@Component.Builder types must have exactly one no-args method that "
              + " returns the @Component type");
    }

    @Override
    String multipleSettersForModuleOrDependencyType() {
      return process(
          "@Component.Builder types must not have more than one setter method per module or "
              + "dependency, but %s is set by %s");
    }

    @Override
    String extraSetters() {
      return process(
          "@Component.Builder has setters for modules or components that aren't required: %s");
    }

    @Override
    String missingSetters() {
      return process(
          "@Component.Builder is missing setters for required modules or components: %s");
    }

    @Override
    String twoFactoryMethods() {
      return process(
          "@Component.Builder types must have exactly one zero-arg method, and that"
              + " method must return the @Component type. Already found: %s");
    }

    @Override
    String inheritedTwoFactoryMethods() {
      return process(
          "@Component.Builder types must have exactly one zero-arg method, and that"
              + " method must return the @Component type. Found %s and %s");
    }

    @Override
    String factoryMethodMustReturnComponentType() {
      return process(
          "@Component.Builder methods that have no arguments must return the @Component type or a "
              + "supertype of the @Component");
    }

    @Override
    String factoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return process(
          "@Component.Builder no-arg build methods may not be annotated with @BindsInstance");
    }

    @Override
    String nonBindsInstanceParametersMayNotBePrimitives() {
      return process(
          "@Component.Builder methods that are not annotated with @BindsInstance "
              + "must take either a module or a component dependency, not a primitive");
    }
  }

  private static final class FactoryMessages extends ComponentCreatorMessages {
    FactoryMessages(Function<String, String> transformation) {
      super(transformation.andThen(FACTORY));
    }

    @Override
    String missingFactoryMethod() {
      return process(
          "@Component.Factory types must have exactly one method that "
              + "returns the @Component type");
    }

    @Override
    String multipleSettersForModuleOrDependencyType() {
      return process(
          "@Component.Factory methods must not have more than one parameter per module or "
              + "dependency, but %s is set by %s");
    }

    @Override
    String extraSetters() {
      return process(
          "@Component.Factory method has parameters for modules or components that aren't "
              + "required: %s");
    }

    @Override
    String missingSetters() {
      return process(
          "@Component.Factory method is missing parameters for required modules or components: %s");
    }

    @Override
    String twoFactoryMethods() {
      return process(
          "@Component.Factory types must have exactly one abstract method. Already found: %s");
    }

    @Override
    String inheritedTwoFactoryMethods() {
      return twoFactoryMethods();
    }

    @Override
    String factoryMethodMustReturnComponentType() {
      return process(
          "@Component.Factory abstract methods must return the @Component type or a "
              + "supertype of the @Component");
    }

    @Override
    String factoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return process("@Component.Factory method may not be annotated with @BindsInstance");
    }

    @Override
    String nonBindsInstanceParametersMayNotBePrimitives() {
      return process(
          "@Component.Factory method parameters that are not annotated with @BindsInstance "
              + "must be either a module or a component dependency, not a primitive");
    }
  }

  private ErrorMessages() {}
}
