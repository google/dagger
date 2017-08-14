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

import static dagger.internal.codegen.ConfigurationAnnotations.getSubcomponentAnnotation;
import static dagger.internal.codegen.MoreAnnotationMirrors.simpleName;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import dagger.multibindings.Multibinds;
import dagger.releasablereferences.CanReleaseReferences;
import dagger.releasablereferences.ForReleasableReferences;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * The collection of error messages to be reported back to users.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ErrorMessages {
  /*
   * Common constants.
   */
  static final String INDENT = "    ";
  static final String DOUBLE_INDENT = INDENT + INDENT;
  static final int DUPLICATE_SIZE_LIMIT = 10;

  /*
   * JSR-330 errors
   *
   * These are errors that are explicitly outlined in the JSR-330 APIs
   */

  /* constructors */
  static final String MULTIPLE_INJECT_CONSTRUCTORS =
      "Types may only contain one @Inject constructor.";

  /* fields */
  static final String FINAL_INJECT_FIELD = "@Inject fields may not be final";

  /* methods */
  static final String ABSTRACT_INJECT_METHOD = "Methods with @Inject may not be abstract.";
  static final String GENERIC_INJECT_METHOD =
      "Methods with @Inject may not declare type parameters.";

  /* qualifiers */
  static final String MULTIPLE_QUALIFIERS =
      "A single injection site may not use more than one @Qualifier.";

  /* scope */
  static final String MULTIPLE_SCOPES = "A single binding may not declare more than one @Scope.";

  /*
   * Dagger errors
   *
   * These are errors that arise due to restrictions imposed by the dagger implementation.
   */

  /* constructors */
  static final String INJECT_ON_PRIVATE_CONSTRUCTOR =
      "Dagger does not support injection into private constructors";
  static final String INJECT_CONSTRUCTOR_ON_INNER_CLASS =
      "@Inject constructors are invalid on inner classes";
  static final String INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS =
      "@Inject is nonsense on the constructor of an abstract class";
  static final String QUALIFIER_ON_INJECT_CONSTRUCTOR =
      "@Qualifier annotations are not allowed on @Inject constructors.";
  static final String SCOPE_ON_INJECT_CONSTRUCTOR =
      "@Scope annotations are not allowed on @Inject constructors. Annotate the class instead.";
  static final String CHECKED_EXCEPTIONS_ON_CONSTRUCTORS =
      "Dagger does not support checked exceptions on @Inject constructors.";

  /* fields */
  static final String PRIVATE_INJECT_FIELD =
      "Dagger does not support injection into private fields";

  static final String STATIC_INJECT_FIELD =
      "Dagger does not support injection into static fields";

  /* methods */
  static final String PRIVATE_INJECT_METHOD =
      "Dagger does not support injection into private methods";

  static final String STATIC_INJECT_METHOD =
      "Dagger does not support injection into static methods";

  /* all */
  static final String INJECT_INTO_PRIVATE_CLASS =
      "Dagger does not support injection into private classes";

  static final String CANNOT_INJECT_WILDCARD_TYPE =
      "Dagger does not support injecting Provider<T>, Lazy<T> or Produced<T> when T is a wildcard "
          + "type such as <%s>.";

  /*
   * Configuration errors
   *
   * These are errors that relate specifically to the Dagger configuration API (@Module, @Provides,
   * etc.)
   */
  static final String DUPLICATE_BINDINGS_FOR_KEY_FORMAT =
      "%s is bound multiple times:";

  static String duplicateMapKeysError(String key) {
    return "The same map key is bound more than once for " + key;
  }

  static String inconsistentMapKeyAnnotationsError(String key) {
    return key + " uses more than one @MapKey annotation type";
  }

  static final String COMPONENT_ANNOTATED_REUSABLE =
      "@Reusable cannot be applied to components or subcomponents.";

  static final String BINDING_METHOD_RETURN_TYPE =
      "@%s methods must return a primitive, an array, a type variable, or a declared type.";

  static final String BINDING_METHOD_THROWS_CHECKED =
      "@%s methods may only throw unchecked exceptions";

  static final String PRODUCES_METHOD_NULLABLE =
      "@Nullable on @Produces methods does not do anything.";

  static final String PRODUCES_METHOD_RETURN_TYPE =
      "@Produces methods must return a primitive, an array, a type variable, or a declared type, "
          + "or a ListenableFuture of one of those types.";

  static final String PRODUCES_METHOD_RAW_FUTURE =
      "@Produces methods cannot return a raw ListenableFuture.";

  static final String BINDING_METHOD_SET_VALUES_RAW_SET =
      "@%s methods of type set values cannot return a raw Set";

  static final String BINDS_ELEMENTS_INTO_SET_METHOD_RAW_SET_PARAMETER =
      "@Binds @ElementsIntoSet methods cannot take a raw Set parameter";

  static final String BINDING_METHOD_SET_VALUES_RETURN_SET =
      "@%s methods of type set values must return a Set";

  static final String PRODUCES_METHOD_SET_VALUES_RETURN_SET =
      "@Produces methods of type set values must return a Set or ListenableFuture of Set";

  static final String PRODUCES_METHOD_SCOPE = "@Produces methods may not have scope annotations.";

  static final String BINDING_METHOD_THROWS =
      "@%s methods may only throw unchecked exceptions or exceptions subclassing Exception";

  static final String BINDING_METHOD_THROWS_ANY = "@%s methods may not throw";

  static final String BINDING_METHOD_MUST_RETURN_A_VALUE =
      "@%s methods must return a value (not void).";

  static final String BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES =
      "@%s methods must not return framework types.";

  static final String BINDING_METHOD_ABSTRACT = "@%s methods cannot be abstract";

  static final String BINDING_METHOD_NOT_ABSTRACT = "@%s methods must be abstract";

  static final String BINDING_METHOD_PRIVATE = "@%s methods cannot be private";

  static final String BINDING_METHOD_TYPE_PARAMETER =
      "@%s methods may not have type parameters.";

  // TODO(ronshapiro): clarify this error message for @ElementsIntoSet cases, where the
  // right-hand-side might not be assignable to the left-hand-side, but still compatible with
  // Set.addAll(Collection<? extends E>)
  static final String BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER =
      "@Binds methods must have only one parameter whose type is assignable to the return type";

  static final String BINDS_OPTIONAL_OF_METHOD_HAS_PARAMETER =
      "@BindsOptionalOf methods must not have parameters";

  static final String BINDS_OPTIONAL_OF_METHOD_RETURNS_IMPLICITLY_PROVIDED_TYPE =
      "@BindsOptionalOf methods cannot "
          + "return unqualified types that have an @Inject-annotated constructor because those are "
          + "always present";

  static final String BINDING_METHOD_NOT_IN_MODULE = "@%s methods can only be present within a @%s";

  static final String BINDS_ELEMENTS_INTO_SET_METHOD_RETURN_SET =
      "@Binds @ElementsIntoSet methods must return a Set and take a Set parameter";

  static final String BINDING_METHOD_NOT_MAP_HAS_MAP_KEY =
      "@%s methods of non map type cannot declare a map key";

  static final String BINDING_METHOD_WITH_NO_MAP_KEY =
      "@%s methods of type map must declare a map key";

  static final String BINDING_METHOD_WITH_MULTIPLE_MAP_KEYS =
      "@%s methods may not have more than one @MapKey-marked annotation";

  static final String BINDING_METHOD_WITH_SAME_NAME =
      "Cannot have more than one @%s method with the same name in a single module";

  static final String INCOMPATIBLE_MODULE_METHODS =
      "A @%1$s may not contain both non-static @%2$s methods and abstract @Binds or @Multibinds "
          + "declarations";

  static final String MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT =
      "Modules with type parameters must be abstract";

  static final String REFERENCED_MODULE_NOT_ANNOTATED =
      "%s is listed as a module, but is not annotated with %s";

  static final String REFERENCED_MODULE_MUST_NOT_HAVE_TYPE_PARAMS =
      "%s is listed as a module, but has type parameters";

  static final String PROVIDES_METHOD_OVERRIDES_ANOTHER =
      "@%s methods may not override another method. Overrides: %s";

  static final String METHOD_OVERRIDES_PROVIDES_METHOD =
      "@%s methods may not be overridden in modules. Overrides: %s";

  static final String BINDING_METHOD_MULTIPLE_QUALIFIERS =
      "Cannot use more than one @Qualifier";

  /* mapKey errors*/
  static final String MAPKEY_WITHOUT_MEMBERS =
      "Map key annotations must have members";

  static final String UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS=
      "Map key annotations with unwrapped values must have exactly one member";

  static final String UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER =
      "Map key annotations with unwrapped values cannot use arrays";

  /* collection binding errors */
  static final String MULTIPLE_CONTRIBUTION_TYPES_FOR_KEY_FORMAT =
      "%s has incompatible bindings or declarations:\n";

  static final String PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision entry-point, which cannot depend on a production.";

  static final String PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision, which cannot depend on a production.";

  static final String DEPENDS_ON_PRODUCTION_EXECUTOR_FORMAT =
      "%s may not depend on the production executor.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides-annotated method.";

  static final String REQUIRES_PROVIDER_FORMAT =
      "%s cannot be provided without an @Provides-annotated method.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides- or "
      + "@Produces-annotated method.";

  static final String REQUIRES_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Provides- or @Produces-annotated method.";

  private static final String PROVISION_MAY_NOT_DEPEND_ON_PRODUCER_TYPE_FORMAT =
      "%s may only be injected in @Produces methods.";

  static String provisionMayNotDependOnProducerType(TypeMirror type) {
    return String.format(
        PROVISION_MAY_NOT_DEPEND_ON_PRODUCER_TYPE_FORMAT,
        MoreTypes.asTypeElement(type).getSimpleName());
  }

  static final String MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION =
      "This type supports members injection but cannot be implicitly provided.";

  static final String MEMBERS_INJECTION_WITH_RAW_TYPE =
      "%s has type parameters, cannot members inject the raw type. via:\n%s";

  static final String MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE =
      "Type parameters must be bounded for members injection. %s required by %s, via:\n%s";

  static final String CONTAINS_DEPENDENCY_CYCLE_FORMAT = "Found a dependency cycle:\n%s";

  static String nullableToNonNullable(String typeName, String bindingString) {
    return String.format(
            "%s is not nullable, but is being provided by %s",
            typeName,
            bindingString);
  }

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD =
      "Cannot return null from a non-@Nullable component method";

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD =
      "Cannot return null from a non-@Nullable @Provides method";

  /* Multibinding messages */
  static final String MULTIBINDING_ANNOTATION_NOT_ON_BINDING_METHOD =
      "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods";

  static final String MULTIPLE_MULTIBINDING_ANNOTATIONS_ON_METHOD =
      "Multiple multibinding annotations cannot be placed on the same %s method";

  static final String MULTIBINDING_ANNOTATION_CONFLICTS_WITH_BINDING_ANNOTATION_ENUM =
      "@%s.type cannot be used with multibinding annotations";

  /* BindsInstance messages. */
  static final String BINDS_INSTANCE_IN_MODULE =
      "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?";

  static final String BINDS_INSTANCE_IN_INVALID_COMPONENT =
      "@BindsInstance methods should not be included in @%1$ss. "
          + "Did you mean to put it in a @%1$s.Builder?";

  static final String BINDS_INSTANCE_ONE_PARAMETER =
      "@BindsInstance methods should have exactly one parameter for the bound type";

  static ComponentBuilderMessages builderMsgsFor(ComponentDescriptor.Kind kind) {
    switch(kind) {
      case COMPONENT:
        return ComponentBuilderMessages.INSTANCE;
      case SUBCOMPONENT:
        return SubcomponentBuilderMessages.INSTANCE;
      case PRODUCTION_COMPONENT:
        return ProductionComponentBuilderMessages.INSTANCE;
      case PRODUCTION_SUBCOMPONENT:
        return ProductionSubcomponentBuilderMessages.INSTANCE;
      default:
        throw new IllegalStateException(kind.toString());
    }
  }

  static final String CAN_RELEASE_REFERENCES_ANNOTATIONS_MUST_NOT_HAVE_SOURCE_RETENTION =
      "@CanReleaseReferences annotations must not have SOURCE retention";

  static String forReleasableReferencesValueNotAScope(TypeElement scopeType) {
    return forReleasableReferencesValueNeedsAnnotation(
        scopeType,
        String.format(
            "@%s and @%s",
            javax.inject.Scope.class.getCanonicalName(),
            CanReleaseReferences.class.getCanonicalName()));
  }

  static String forReleasableReferencesValueCannotReleaseReferences(TypeElement scopeType) {
    return forReleasableReferencesValueNeedsAnnotation(
        scopeType, "@" + CanReleaseReferences.class.getCanonicalName());
  }

  private static String forReleasableReferencesValueNeedsAnnotation(
      TypeElement scopeType, String annotations) {
    return String.format(
        "The value of @%s must be a reference-releasing scope. "
            + "Did you mean to annotate %s with %s? Or did you mean to use a different class here?",
        ForReleasableReferences.class.getSimpleName(), scopeType.getQualifiedName(), annotations);
  }

  static String referenceReleasingScopeNotInComponentHierarchy(
      String formattedKey, Scope scope, BindingGraph topLevelGraph) {
    return String.format(
        "There is no binding for %s because no component in %s's component hierarchy is "
            + "annotated with %s. The available reference-releasing scopes are %s.",
        formattedKey,
        topLevelGraph.componentType().getQualifiedName(),
        scope.getReadableSource(),
        topLevelGraph
            .componentDescriptor()
            .releasableReferencesScopes()
            .stream()
            .map(Scope::getReadableSource)
            .collect(toList()));
  }

  static String referenceReleasingScopeMetadataMissingCanReleaseReferences(
      String formattedKey, DeclaredType metadataType) {
    return String.format(
        "There is no binding for %s because %s is not annotated with @%s.",
        formattedKey, metadataType, CanReleaseReferences.class.getCanonicalName());
  }

  static String referenceReleasingScopeNotAnnotatedWithMetadata(
      String formattedKey, Scope scope, TypeMirror metadataType) {
    return String.format(
        "There is no binding for %s because %s is not annotated with @%s.",
        formattedKey, scope.getQualifiedName(), metadataType);
  }

  /**
   * Returns an error message for a method that has more than one binding method annotation.
   *
   * @param methodAnnotations the valid method annotations, only one of which may annotate the
   *     method
   */
  static String tooManyBindingMethodAnnotations(
      ExecutableElement method, Collection<Class<? extends Annotation>> methodAnnotations) {
    return String.format(
        "%s is annotated with more than one of (%s)",
        method.getSimpleName(),
        methodAnnotations.stream().map(Class::getCanonicalName).collect(joining(", ")));
  }

  static String abstractModuleHasInstanceBindingMethods(ModuleDescriptor module) {
    String methodAnnotations;
    switch (module.kind()) {
      case MODULE:
        methodAnnotations = "@Provides";
        break;
      case PRODUCER_MODULE:
        methodAnnotations = "@Provides or @Produces";
        break;
      default:
        throw new AssertionError(module.kind());
    }
    return String.format(
        "%s is abstract and has instance %s methods. Consider making the methods static or "
            + "including a non-abstract subclass of the module instead.",
        module.moduleElement(), methodAnnotations);
  }

  static class ComponentBuilderMessages {
    static final ComponentBuilderMessages INSTANCE = new ComponentBuilderMessages();

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
      return process(buildMustReturnComponentType() + ". Inherited method: %s");
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

    final String buildMethodReturnsSupertypeWithMissingMethods(
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
  }

  static final class SubcomponentBuilderMessages extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final SubcomponentBuilderMessages INSTANCE = new SubcomponentBuilderMessages();

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

  private static final class ProductionComponentBuilderMessages extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final ProductionComponentBuilderMessages INSTANCE =
        new ProductionComponentBuilderMessages();

    @Override protected String process(String s) {
      return s.replaceAll("component", "production component")
          .replaceAll("Component", "ProductionComponent");
    }
  }

  private static final class ProductionSubcomponentBuilderMessages
      extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final ProductionSubcomponentBuilderMessages INSTANCE =
        new ProductionSubcomponentBuilderMessages();

    @Override
    protected String process(String s) {
      return s.replaceAll("component", "production subcomponent")
          .replaceAll("Component", "ProductionSubcomponent");
    }
  }

  /** Error messages related to {@link Multibinds @Multibinds} methods. */
  static final class MultibindsMessages {
    static final String METHOD_MUST_RETURN_MAP_OR_SET =
        "@%s methods must return Map<K, V> or Set<T>";

    static final String PARAMETERS = "@%s methods cannot have parameters";

    private MultibindsMessages() {}
  }

  static class ModuleMessages {
    static String moduleSubcomponentsIncludesBuilder(TypeElement moduleSubcomponentsAttribute) {
      TypeElement subcomponentType =
          MoreElements.asType(moduleSubcomponentsAttribute.getEnclosingElement());
      return String.format(
          "%s is a @%s.Builder. Did you mean to use %s?",
          moduleSubcomponentsAttribute.getQualifiedName(),
          simpleName(getSubcomponentAnnotation(subcomponentType).get()),
          subcomponentType.getQualifiedName());
    }

    static String moduleSubcomponentsIncludesNonSubcomponent(
        TypeElement moduleSubcomponentsAttribute) {
      return moduleSubcomponentsAttribute.getQualifiedName()
          + " is not a @Subcomponent or @ProductionSubcomponent";
    }

    static String moduleSubcomponentsDoesntHaveBuilder(
        TypeElement subcomponent, AnnotationMirror moduleAnnotation) {
      return String.format(
          "%s doesn't have a @%s.Builder, which is required when used with @%s.subcomponents",
          subcomponent.getQualifiedName(),
          simpleName(getSubcomponentAnnotation(subcomponent).get()),
          simpleName(moduleAnnotation));
    }
  }

  /**
   * A regular expression to match a small list of specific packages deemed to
   * be unhelpful to display in fully qualified types in error messages.
   *
   * Note: This should never be applied to messages themselves.
   */
  private static final Pattern COMMON_PACKAGE_PATTERN = Pattern.compile(
      "(?:^|[^.a-z_])"     // What we want to match on but not capture.
      + "((?:"             // Start a group with a non-capturing or part
      + "java[.]lang"
      + "|java[.]util"
      + "|javax[.]inject"
      + "|dagger"
      + "|com[.]google[.]common[.]base"
      + "|com[.]google[.]common[.]collect"
      + ")[.])"            // Always end with a literal .
      + "[A-Z]");           // What we want to match on but not capture.

  /**
   * A method to strip out common packages and a few rare type prefixes
   * from types' string representation before being used in error messages.
   *
   * This type assumes a String value that is a valid fully qualified
   * (and possibly parameterized) type, and should NOT be used with
   * arbitrary text, especially prose error messages.
   *
   * TODO(cgruber): Tighten these to take type representations (mirrors
   *     and elements) to avoid accidental mis-use by running errors
   *     through this method.
   */
  static String stripCommonTypePrefixes(String type) {
    // Do regex magic to remove common packages we care to shorten.
    Matcher matcher = COMMON_PACKAGE_PATTERN.matcher(type);
    StringBuilder result = new StringBuilder();
    int index = 0;
    while (matcher.find()) {
      result.append(type.subSequence(index, matcher.start(1)));
      index = matcher.end(1); // Skip the matched pattern content.
    }
    result.append(type.subSequence(index, type.length()));
    return result.toString();
  }

  //TODO(cgruber): Extract Formatter and do something less stringy.
  static String format(AnnotationMirror annotation) {
    return stripCommonTypePrefixes(annotation.toString());
  }

  private ErrorMessages() {}
}
