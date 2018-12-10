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
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * The collection of error messages to be reported back to users.
 */
final class ErrorMessages {

  static ComponentCreatorMessages creatorMessagesFor(ComponentKind kind) {
    switch(kind) {
      case COMPONENT:
        return ComponentCreatorMessages.INSTANCE;
      case SUBCOMPONENT:
        return SubcomponentCreatorMessages.INSTANCE;
      case PRODUCTION_COMPONENT:
        return ProductionComponentCreatorMessages.INSTANCE;
      case PRODUCTION_SUBCOMPONENT:
        return ProductionSubcomponentCreatorMessages.INSTANCE;
      default:
        throw new IllegalStateException(kind.toString());
    }
  }

  static class ComponentCreatorMessages {
    static final ComponentCreatorMessages INSTANCE = new ComponentCreatorMessages();

    protected String process(String s) { return s; }

    /** Errors for component builders. */
    final String moreThanOne() {
      return process("@Component has more than one @Component.Builder: %s");
    }

    final String cxtorOnlyOneAndNoArgs() {
      return process("@Component.Builder classes must have exactly one constructor,"
          + " and it must not have any parameters");
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

    final String missingBuildMethod() {
      return process("@Component.Builder types must have exactly one no-args method that "
          + " returns the @Component type");
    }

    final String manyMethodsForType() {
      return process("@Component.Builder types must not have more than one setter method per type,"
          + " but %s is set by %s");
    }

    final String extraSetters() {
      return process(
          "@Component.Builder has setters for modules or components that aren't required: %s");
    }

    final String missingSetters() {
      return process(
          "@Component.Builder is missing setters for required modules or components: %s");
    }

    final String twoBuildMethods() {
      return process("@Component.Builder types must have exactly one zero-arg method, and that"
          + " method must return the @Component type. Already found: %s");
    }

    final String inheritedTwoBuildMethods() {
      return process("@Component.Builder types must have exactly one zero-arg method, and that"
          + " method must return the @Component type. Found %s and %s");
    }

    final String buildMustReturnComponentType() {
      return process(
          "@Component.Builder methods that have no arguments must return the @Component type or a "
              + "supertype of the @Component");
    }

    final String inheritedBuildMustReturnComponentType() {
      return buildMustReturnComponentType() + ". Inherited method: %s";
    }

    final String methodsMustTakeOneArg() {
      return process("@Component.Builder methods must not have more than one argument");
    }

    final String inheritedMethodsMustTakeOneArg() {
      return process(
          "@Component.Builder methods must not have more than one argument. Inherited method: %s");
    }

    final String methodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + " or a supertype of the builder");
    }

    final String inheritedMethodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + "or a supertype of the builder. Inherited method: %s");
    }

    final String methodsMayNotHaveTypeParameters() {
      return process("@Component.Builder methods must not have type parameters");
    }

    final String inheritedMethodsMayNotHaveTypeParameters() {
      return process(
          "@Component.Builder methods must not have type parameters. Inherited method: %s");
    }

    final String nonBindsInstanceMethodsMayNotTakePrimitives() {
      return process(
          "@Component.Builder methods that are not annotated with @BindsInstance "
              + "must take either a module or a component dependency, not a primitive");
    }

    final String inheritedNonBindsInstanceMethodsMayNotTakePrimitives() {
      return nonBindsInstanceMethodsMayNotTakePrimitives() + process(". Inherited method: %s");
    }

    final String buildMethodReturnsSupertypeWithMissingMethods(
        TypeElement component,
        TypeElement componentCreator,
        TypeMirror returnType,
        ExecutableElement buildMethod,
        Set<ExecutableElement> additionalMethods) {
      return String.format(
          "%1$s.%2$s() returns %3$s, but %4$s declares additional component method(s): %5$s. In "
              + "order to provide type-safe access to these methods, override %2$s() to return "
              + "%4$s",
          componentCreator.getQualifiedName(),
          buildMethod.getSimpleName(),
          returnType,
          component.getQualifiedName(),
          Joiner.on(", ").join(additionalMethods));
    }
  }

  static final class SubcomponentCreatorMessages extends ComponentCreatorMessages {
    @SuppressWarnings("hiding")
    static final SubcomponentCreatorMessages INSTANCE = new SubcomponentCreatorMessages();

    @Override protected String process(String s) {
      return s.replaceAll("component", "subcomponent").replaceAll("Component", "Subcomponent");
    }

    String builderMethodRequiresNoArgs() {
      return "Methods returning a @Subcomponent.Builder must have no arguments";
    }

    String moreThanOneRefToSubcomponent() {
      return "Only one method can create a given subcomponent. %s is created by: %s";
    }
  }

  private static final class ProductionComponentCreatorMessages extends ComponentCreatorMessages {
    @SuppressWarnings("hiding")
    static final ProductionComponentCreatorMessages INSTANCE =
        new ProductionComponentCreatorMessages();

    @Override protected String process(String s) {
      return s.replaceAll("component", "production component")
          .replaceAll("Component", "ProductionComponent");
    }
  }

  private static final class ProductionSubcomponentCreatorMessages
      extends ComponentCreatorMessages {
    @SuppressWarnings("hiding")
    static final ProductionSubcomponentCreatorMessages INSTANCE =
        new ProductionSubcomponentCreatorMessages();

    @Override
    protected String process(String s) {
      return s.replaceAll("component", "production subcomponent")
          .replaceAll("Component", "ProductionSubcomponent");
    }
  }

  private ErrorMessages() {}
}
