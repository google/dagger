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

import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.internal.codegen.base.ComponentKind;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The collection of error messages to be reported back to users. */
public final class ErrorMessages {

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

  public static ComponentMessages componentMessagesFor(ComponentKind componentKind) {
    return new ComponentMessages(COMPONENT_TRANSFORMATIONS.get(componentKind));
  }

  public static ComponentMessages componentMessagesFor(ComponentAnnotation componentAnnotation) {
    return new ComponentMessages(
        transformation(componentAnnotation.isProduction(), componentAnnotation.isSubcomponent()));
  }

  public static ComponentCreatorMessages creatorMessagesFor(
      ComponentCreatorAnnotation creatorAnnotation) {
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
  public static final class ComponentMessages extends Messages {
    ComponentMessages(Function<String, String> transformation) {
      super(transformation);
    }

    public final String moreThanOne() {
      return process("@Component has more than one @Component.Builder or @Component.Factory: %s");
    }
  }

  /** Errors for component creators. */
  public abstract static class ComponentCreatorMessages extends Messages {
    ComponentCreatorMessages(Function<String, String> transformation) {
      super(transformation);
    }

    public static String builderMethodRequiresNoArgs() {
      return "Methods returning a @Component.Builder must have no arguments";
    }

    public static String moreThanOneRefToSubcomponent() {
      return "Only one method can create a given subcomponent. %s is created by: %s";
    }

    public final String invalidConstructor() {
      return process("@Component.Builder classes must have exactly one constructor,"
          + " and it must not be private or have any parameters");
    }

    public final String generics() {
      return process("@Component.Builder types must not have any generic types");
    }

    public final String mustBeInComponent() {
      return process("@Component.Builder types must be nested within a @Component");
    }

    public final String mustBeClassOrInterface() {
      return process("@Component.Builder types must be abstract classes or interfaces");
    }

    public final String isPrivate() {
      return process("@Component.Builder types must not be private");
    }

    public final String mustBeStatic() {
      return process("@Component.Builder types must be static");
    }

    public final String mustBeAbstract() {
      return process("@Component.Builder types must be abstract");
    }

    public abstract String missingFactoryMethod();

    public abstract String multipleSettersForModuleOrDependencyType();

    public abstract String extraSetters();

    public abstract String missingSetters();

    public abstract String twoFactoryMethods();

    public abstract String inheritedTwoFactoryMethods();

    public abstract String factoryMethodMustReturnComponentType();

    public final String inheritedFactoryMethodMustReturnComponentType() {
      return factoryMethodMustReturnComponentType() + ". Inherited method: %s";
    }

    public abstract String factoryMethodMayNotBeAnnotatedWithBindsInstance();

    public final String inheritedFactoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return factoryMethodMayNotBeAnnotatedWithBindsInstance() + ". Inherited method: %s";
    }

    public final String setterMethodsMustTakeOneArg() {
      return process("@Component.Builder methods must not have more than one argument");
    }

    public final String inheritedSetterMethodsMustTakeOneArg() {
      return setterMethodsMustTakeOneArg() + ". Inherited method: %s";
    }

    public final String setterMethodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + " or a supertype of the builder");
    }

    public final String inheritedSetterMethodsMustReturnVoidOrBuilder() {
      return setterMethodsMustReturnVoidOrBuilder() + ". Inherited method: %s";
    }

    public final String methodsMayNotHaveTypeParameters() {
      return process("@Component.Builder methods must not have type parameters");
    }

    public final String inheritedMethodsMayNotHaveTypeParameters() {
      return methodsMayNotHaveTypeParameters() + ". Inherited method: %s";
    }

    public abstract String nonBindsInstanceParametersMayNotBePrimitives();

    public final String inheritedNonBindsInstanceParametersMayNotBePrimitives() {
      return nonBindsInstanceParametersMayNotBePrimitives() + ". Inherited method: %s";
    }

    public final String factoryMethodReturnsSupertypeWithMissingMethods(
        XTypeElement component,
        XTypeElement componentBuilder,
        XType returnType,
        XMethodElement buildMethod,
        ImmutableCollection<XMethodElement> additionalMethods) {
      return String.format(
          "%1$s.%2$s() returns %3$s, but %4$s declares additional component method(s): %5$s. In "
              + "order to provide type-safe access to these methods, override %2$s() to return "
              + "%4$s",
          componentBuilder.getQualifiedName(),
          getSimpleName(buildMethod),
          returnType.getTypeName(),
          component.getQualifiedName(),
          additionalMethods.stream()
              .map(method -> getSimpleName(method) + "()")
              .collect(joining(", ")));
    }

    public final String bindsInstanceNotAllowedOnBothSetterMethodAndParameter() {
      return process("@Component.Builder setter methods may not have @BindsInstance on both the "
          + "method and its parameter; choose one or the other");
    }

    public final String inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter() {
      return bindsInstanceNotAllowedOnBothSetterMethodAndParameter() + ". Inherited method: %s";
    }
  }

  private static final class BuilderMessages extends ComponentCreatorMessages {
    BuilderMessages(Function<String, String> transformation) {
      super(transformation);
    }

    @Override
    public String missingFactoryMethod() {
      return process(
          "@Component.Builder types must have exactly one no-args method that returns the "
              + "@Component type");
    }

    @Override
    public String multipleSettersForModuleOrDependencyType() {
      return process(
          "@Component.Builder types must not have more than one setter method per module or "
              + "dependency, but %s is set by %s");
    }

    @Override
    public String extraSetters() {
      return process(
          "@Component.Builder has setters for modules or components that aren't required: %s");
    }

    @Override
    public String missingSetters() {
      return process(
          "@Component.Builder is missing setters for required modules or components: %s");
    }

    @Override
    public String twoFactoryMethods() {
      return process(
          "@Component.Builder types must have exactly one zero-arg method, and that"
              + " method must return the @Component type. Already found: %s");
    }

    @Override
    public String inheritedTwoFactoryMethods() {
      return process(
          "@Component.Builder types must have exactly one zero-arg method, and that"
              + " method must return the @Component type. Found %s and %s");
    }

    @Override
    public String factoryMethodMustReturnComponentType() {
      return process(
          "@Component.Builder methods that have no arguments must return the @Component type or a "
              + "supertype of the @Component");
    }

    @Override
    public String factoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return process(
          "@Component.Builder no-arg build methods may not be annotated with @BindsInstance");
    }

    @Override
    public String nonBindsInstanceParametersMayNotBePrimitives() {
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
    public String missingFactoryMethod() {
      return process(
          "@Component.Factory types must have exactly one method that "
              + "returns the @Component type");
    }

    @Override
    public String multipleSettersForModuleOrDependencyType() {
      return process(
          "@Component.Factory methods must not have more than one parameter per module or "
              + "dependency, but %s is set by %s");
    }

    @Override
    public String extraSetters() {
      return process(
          "@Component.Factory method has parameters for modules or components that aren't "
              + "required: %s");
    }

    @Override
    public String missingSetters() {
      return process(
          "@Component.Factory method is missing parameters for required modules or components: %s");
    }

    @Override
    public String twoFactoryMethods() {
      return process(
          "@Component.Factory types must have exactly one abstract method. Already found: %s");
    }

    @Override
    public String inheritedTwoFactoryMethods() {
      return twoFactoryMethods();
    }

    @Override
    public String factoryMethodMustReturnComponentType() {
      return process(
          "@Component.Factory abstract methods must return the @Component type or a "
              + "supertype of the @Component");
    }

    @Override
    public String factoryMethodMayNotBeAnnotatedWithBindsInstance() {
      return process("@Component.Factory method may not be annotated with @BindsInstance");
    }

    @Override
    public String nonBindsInstanceParametersMayNotBePrimitives() {
      return process(
          "@Component.Factory method parameters that are not annotated with @BindsInstance "
              + "must be either a module or a component dependency, not a primitive");
    }
  }

  private ErrorMessages() {}
}
