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

package dagger.internal.codegen.validation;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentAnnotation.isComponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.getCreatorAnnotations;
import static dagger.internal.codegen.base.ModuleAnnotation.isModuleAnnotation;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.getSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.validation.ModuleValidator.ModuleMethodKind.ABSTRACT_DECLARATION;
import static dagger.internal.codegen.validation.ModuleValidator.ModuleMethodKind.INSTANCE_BINDING;
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XTypeElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XTypeElements.isEffectivelyPrivate;
import static dagger.internal.codegen.xprocessing.XTypeElements.isEffectivelyPublic;
import static dagger.internal.codegen.xprocessing.XTypes.areEquivalentTypes;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@linkplain ValidationReport validator} for {@link dagger.Module}s or {@link
 * dagger.producers.ProducerModule}s.
 */
@Singleton
public final class ModuleValidator {
  private static final ImmutableSet<XClassName> SUBCOMPONENT_TYPES =
      ImmutableSet.of(XTypeNames.SUBCOMPONENT, XTypeNames.PRODUCTION_SUBCOMPONENT);
  private static final ImmutableSet<XClassName> SUBCOMPONENT_CREATOR_TYPES =
      ImmutableSet.of(
          XTypeNames.SUBCOMPONENT_BUILDER,
          XTypeNames.SUBCOMPONENT_FACTORY,
          XTypeNames.PRODUCTION_SUBCOMPONENT_BUILDER,
          XTypeNames.PRODUCTION_SUBCOMPONENT_FACTORY);
  private static final Optional<Class<?>> ANDROID_PROCESSOR;
  private static final XClassName CONTRIBUTES_ANDROID_INJECTOR_NAME =
      XClassName.get("dagger.android", "ContributesAndroidInjector");
  private static final String ANDROID_PROCESSOR_NAME = "dagger.android.processor.AndroidProcessor";

  static {
    Class<?> clazz;
    try {
      clazz = Class.forName(ANDROID_PROCESSOR_NAME, false, ModuleValidator.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      clazz = null;
    }
    ANDROID_PROCESSOR = Optional.ofNullable(clazz);
  }

  private final AnyBindingMethodValidator anyBindingMethodValidator;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final BindingGraphValidator bindingGraphValidator;
  private final InjectionAnnotations injectionAnnotations;
  private final DaggerSuperficialValidation superficialValidation;
  private final XProcessingEnv processingEnv;
  private final Map<XTypeElement, ValidationReport> cache = new HashMap<>();
  private final Set<XTypeElement> knownModules = new HashSet<>();

  @Inject
  ModuleValidator(
      AnyBindingMethodValidator anyBindingMethodValidator,
      MethodSignatureFormatter methodSignatureFormatter,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      BindingGraphValidator bindingGraphValidator,
      InjectionAnnotations injectionAnnotations,
      DaggerSuperficialValidation superficialValidation,
      XProcessingEnv processingEnv) {
    this.anyBindingMethodValidator = anyBindingMethodValidator;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.bindingGraphValidator = bindingGraphValidator;
    this.injectionAnnotations = injectionAnnotations;
    this.superficialValidation = superficialValidation;
    this.processingEnv = processingEnv;
  }

  /**
   * Adds {@code modules} to the set of module types that will be validated during this compilation
   * step. If a component or module includes a module that is not in this set, that included module
   * is assumed to be valid because it was processed in a previous compilation step. If it were
   * invalid, that previous compilation step would have failed and blocked this one.
   *
   * <p>This logic depends on this method being called before {@linkplain #validate(XTypeElement)
   * validating} any module or {@linkplain #validateReferencedModules(XTypeElement, ModuleKind, Set,
   * DiagnosticReporter.Builder) component}.
   */
  public void addKnownModules(Collection<XTypeElement> modules) {
    knownModules.addAll(modules);
  }

  /** Returns a validation report for a module type. */
  public ValidationReport validate(XTypeElement module) {
    return validate(module, new HashSet<>());
  }

  private ValidationReport validate(XTypeElement module, Set<XTypeElement> visitedModules) {
    if (visitedModules.add(module)) {
      return reentrantComputeIfAbsent(cache, module, m -> validateUncached(module, visitedModules));
    }
    return ValidationReport.about(module).build();
  }

  private ValidationReport validateUncached(XTypeElement module, Set<XTypeElement> visitedModules) {
    ValidationReport.Builder builder = ValidationReport.about(module);
    ModuleKind moduleKind = ModuleKind.forAnnotatedElement(module).get();
    List<XMethodElement> moduleMethods = module.getDeclaredMethods();
    List<XMethodElement> bindingMethods = new ArrayList<>();
    for (XMethodElement moduleMethod : moduleMethods) {
      if (anyBindingMethodValidator.isBindingMethod(moduleMethod)) {
        builder.addSubreport(anyBindingMethodValidator.validate(moduleMethod));
        bindingMethods.add(moduleMethod);
      }
    }

    validateKotlinObjectDoesNotInheritInstanceBindingMethods(module, moduleKind, builder);
    validateDaggerAndroidProcessorRequirements(module, builder);

    if (bindingMethods.stream()
        .map(ModuleMethodKind::ofMethod)
        .collect(toImmutableSet())
        .containsAll(EnumSet.of(ABSTRACT_DECLARATION, INSTANCE_BINDING))) {
      builder.addError(
          String.format(
              "A @%s may not contain both non-static and abstract binding methods",
              moduleKind.annotation().getSimpleName()));
    }

    validateModuleVisibility(module, moduleKind, builder);

    ImmutableListMultimap<String, XMethodElement> bindingMethodsByName =
        Multimaps.index(bindingMethods, XElements::getSimpleName);

    validateMethodsWithSameName(builder, bindingMethodsByName);
    if (!module.isInterface()) {
      validateBindingMethodOverrides(
          module,
          builder,
          Multimaps.index(moduleMethods, XElements::getSimpleName),
          bindingMethodsByName);
    }
    validateModifiers(module, builder);
    validateReferencedModules(module, moduleKind, visitedModules, builder);
    validateReferencedSubcomponents(module, moduleKind, builder);
    validateNoScopeAnnotationsOnModuleElement(module, moduleKind, builder);
    validateSelfCycles(module, moduleKind, builder);
    module.getEnclosedTypeElements().stream()
        .filter(XTypeElement::isCompanionObject)
        .collect(toOptional())
        .ifPresent(companionModule -> validateCompanionModule(companionModule, builder));

    if (builder.build().isClean()
        && bindingGraphValidator.shouldDoFullBindingGraphValidation(module)) {
      validateModuleBindings(module, builder);
    }

    return builder.build();
  }

  private void validateDaggerAndroidProcessorRequirements(
      XTypeElement module, ValidationReport.Builder builder) {
    if (ANDROID_PROCESSOR.isPresent()
        || processingEnv.findTypeElement(CONTRIBUTES_ANDROID_INJECTOR_NAME) == null) {
      return;
    }
    module.getDeclaredMethods().stream()
        .filter(method -> method.hasAnnotation(CONTRIBUTES_ANDROID_INJECTOR_NAME))
        .forEach(
            method ->
                builder.addSubreport(
                    ValidationReport.about(method)
                        .addError(
                            String.format(
                                "@%s was used, but %s was not found on the processor path",
                                CONTRIBUTES_ANDROID_INJECTOR_NAME.getSimpleName(),
                                ANDROID_PROCESSOR_NAME))
                        .build()));
  }

  private void validateKotlinObjectDoesNotInheritInstanceBindingMethods(
      XTypeElement module, ModuleKind moduleKind, ValidationReport.Builder builder) {
    if (!module.isKotlinObject()) {
      return;
    }
    XTypeElement currentClass = module;
    while (!currentClass.getSuperType().asTypeName().equals(XTypeName.ANY_OBJECT)) {
      currentClass = currentClass.getSuperType().getTypeElement();
      currentClass.getDeclaredMethods().stream()
          .filter(anyBindingMethodValidator::isBindingMethod)
          .filter(method -> ModuleMethodKind.ofMethod(method) == INSTANCE_BINDING)
          .forEach(
              method ->
                  // TODO(b/264618194): Consider allowing this use case.
                  builder.addError(
                      String.format(
                          "@%s-annotated Kotlin object cannot inherit instance (i.e. non-abstract, "
                              + "non-JVM static) binding method: %s",
                          moduleKind.annotation().getSimpleName(),
                          methodSignatureFormatter.format(method))));
    }
  }

  private void validateReferencedSubcomponents(
      XTypeElement subject, ModuleKind moduleKind, ValidationReport.Builder builder) {
    XAnnotation moduleAnnotation = moduleKind.getModuleAnnotation(subject);
    for (XAnnotationValue subcomponentValue :
        moduleAnnotation.getAsAnnotationValueList("subcomponents")) {
      XType type = subcomponentValue.asType();
      if (!isDeclared(type)) {
        builder.addError(
            type + " is not a valid subcomponent type",
            subject,
            moduleAnnotation,
            subcomponentValue);
        continue;
      }

      XTypeElement subcomponentElement = type.getTypeElement();
      if (hasAnyAnnotation(subcomponentElement, SUBCOMPONENT_TYPES)) {
        validateSubcomponentHasBuilder(subject, subcomponentElement, moduleAnnotation, builder);
      } else {
        builder.addError(
            hasAnyAnnotation(subcomponentElement, SUBCOMPONENT_CREATOR_TYPES)
                ? moduleSubcomponentsIncludesCreator(subcomponentElement)
                : moduleSubcomponentsIncludesNonSubcomponent(subcomponentElement),
            subject,
            moduleAnnotation,
            subcomponentValue);
      }
    }
  }

  private static String moduleSubcomponentsIncludesNonSubcomponent(XTypeElement notSubcomponent) {
    return notSubcomponent.getQualifiedName()
        + " is not a @Subcomponent or @ProductionSubcomponent";
  }

  private String moduleSubcomponentsIncludesCreator(XTypeElement moduleSubcomponentsAttribute) {
    XTypeElement subcomponentType = moduleSubcomponentsAttribute.getEnclosingTypeElement();
    ComponentCreatorAnnotation creatorAnnotation =
        getOnlyElement(getCreatorAnnotations(moduleSubcomponentsAttribute));
    return String.format(
        "%s is a @%s.%s. Did you mean to use %s?",
        moduleSubcomponentsAttribute.getQualifiedName(),
        subcomponentAnnotation(subcomponentType, superficialValidation).get().simpleName(),
        creatorAnnotation.creatorKind().typeName(),
        subcomponentType.getQualifiedName());
  }

  private void validateSubcomponentHasBuilder(
      XTypeElement subject,
      XTypeElement subcomponentAttribute,
      XAnnotation moduleAnnotation,
      ValidationReport.Builder builder) {
    if (getSubcomponentCreator(subcomponentAttribute).isPresent()) {
      return;
    }
    builder.addError(
        moduleSubcomponentsDoesntHaveCreator(subcomponentAttribute, moduleAnnotation),
        subject,
        moduleAnnotation);
  }

  private String moduleSubcomponentsDoesntHaveCreator(
      XTypeElement subcomponent, XAnnotation moduleAnnotation) {
    return String.format(
        "%1$s doesn't have a @%2$s.Builder or @%2$s.Factory, which is required when used with "
            + "@%3$s.subcomponents",
        subcomponent.getQualifiedName(),
        subcomponentAnnotation(subcomponent, superficialValidation).get().simpleName(),
        getClassName(moduleAnnotation).simpleName());
  }

  enum ModuleMethodKind {
    ABSTRACT_DECLARATION,
    INSTANCE_BINDING,
    STATIC_BINDING,
    ;

    static ModuleMethodKind ofMethod(XMethodElement moduleMethod) {
      if (moduleMethod.isStatic()) {
        return STATIC_BINDING;
      } else if (moduleMethod.isAbstract()) {
        return ABSTRACT_DECLARATION;
      } else {
        return INSTANCE_BINDING;
      }
    }
  }

  private void validateModifiers(XTypeElement subject, ValidationReport.Builder builder) {
    // This coupled with the check for abstract modules in ComponentValidator guarantees that
    // only modules without type parameters are referenced from @Component(modules={...}).
    if (hasTypeParameters(subject) && !subject.isAbstract()) {
      builder.addError("Modules with type parameters must be abstract", subject);
    }
  }

  private void validateMethodsWithSameName(
      ValidationReport.Builder builder, ListMultimap<String, XMethodElement> bindingMethodsByName) {
    bindingMethodsByName.asMap().values().stream()
        .filter(methods -> methods.size() > 1)
        .flatMap(Collection::stream)
        .forEach(
            duplicateMethod -> {
              builder.addError(
                  "Cannot have more than one binding method with the same name in a single module",
                  duplicateMethod);
            });
  }

  private void validateReferencedModules(
      XTypeElement subject,
      ModuleKind moduleKind,
      Set<XTypeElement> visitedModules,
      ValidationReport.Builder builder) {
    // Validate that all the modules we include are valid for inclusion.
    XAnnotation mirror = moduleKind.getModuleAnnotation(subject);
    builder.addSubreport(
        validateReferencedModules(
            subject, mirror, moduleKind.legalIncludedModuleKinds(), visitedModules));
  }

  /**
   * Validates modules included in a given module or installed in a given component.
   *
   * <p>Checks that the referenced modules are non-generic types annotated with {@code @Module} or
   * {@code @ProducerModule}.
   *
   * <p>If the referenced module is in the {@linkplain #addKnownModules(Collection) known modules
   * set} and has errors, reports an error at that module's inclusion.
   *
   * @param annotatedType the annotated module or component
   * @param annotation the annotation specifying the referenced modules ({@code @Component},
   *     {@code @ProductionComponent}, {@code @Subcomponent}, {@code @ProductionSubcomponent},
   *     {@code @Module}, or {@code @ProducerModule})
   * @param validModuleKinds the module kinds that the annotated type is permitted to include
   */
  ValidationReport validateReferencedModules(
      XTypeElement annotatedType,
      XAnnotation annotation,
      ImmutableSet<ModuleKind> validModuleKinds,
      Set<XTypeElement> visitedModules) {
    superficialValidation.validateAnnotationOf(annotatedType, annotation);

    ValidationReport.Builder subreport = ValidationReport.about(annotatedType);
    // TODO(bcorso): Consider creating a DiagnosticLocation object to encapsulate the location in a
    // single object to avoid duplication across all reported errors
    for (XAnnotationValue includedModule : getModules(annotation)) {
      XType type = includedModule.asType();
      if (!isDeclared(type)) {
        subreport.addError(
            String.format("%s is not a valid module type.", type),
            annotatedType,
            annotation,
            includedModule);
        continue;
      }

      XTypeElement module = type.getTypeElement();
      if (hasTypeParameters(module)) {
        subreport.addError(
            String.format(
                "%s is listed as a module, but has type parameters", module.getQualifiedName()),
            annotatedType,
            annotation,
            includedModule);
      }

      ImmutableSet<XClassName> validModuleAnnotations =
          validModuleKinds.stream().map(ModuleKind::annotation).collect(toImmutableSet());
      if (!hasAnyAnnotation(module, validModuleAnnotations)) {
        subreport.addError(
            String.format(
                "%s is listed as a module, but is not annotated with %s",
                module.getQualifiedName(),
                (validModuleAnnotations.size() > 1 ? "one of " : "")
                    + validModuleAnnotations.stream()
                        .map(otherClass -> "@" + otherClass.getSimpleName())
                        .collect(joining(", "))),
            annotatedType,
            annotation,
            includedModule);
      } else if (knownModules.contains(module) && !validate(module, visitedModules).isClean()) {
        subreport.addError(
            String.format("%s has errors", module.getQualifiedName()),
            annotatedType,
            annotation,
            includedModule);
      }
      if (module.isCompanionObject()) {
        subreport.addError(
            String.format(
                "%s is listed as a module, but it is a companion object class. "
                    + "Add @Module to the enclosing class and reference that instead.",
                module.getQualifiedName()),
            annotatedType,
            annotation,
            includedModule);
      }
    }
    return subreport.build();
  }

  private static ImmutableList<XAnnotationValue> getModules(XAnnotation annotation) {
    if (isModuleAnnotation(annotation)) {
      return ImmutableList.copyOf(annotation.getAsAnnotationValueList("includes"));
    }
    if (isComponentAnnotation(annotation)) {
      return ImmutableList.copyOf(annotation.getAsAnnotationValueList("modules"));
    }
    throw new IllegalArgumentException(String.format("unsupported annotation: %s", annotation));
  }

  private void validateBindingMethodOverrides(
      XTypeElement subject,
      ValidationReport.Builder builder,
      ImmutableListMultimap<String, XMethodElement> moduleMethodsByName,
      ImmutableListMultimap<String, XMethodElement> bindingMethodsByName) {
    // For every binding method, confirm it overrides nothing *and* nothing overrides it.
    // Consider the following hierarchy:
    // class Parent {
    //    @Provides Foo a() {}
    //    @Provides Foo b() {}
    //    Foo c() {}
    // }
    // class Child extends Parent {
    //    @Provides Foo a() {}
    //    Foo b() {}
    //    @Provides Foo c() {}
    // }
    // In each of those cases, we want to fail.  "a" is clear, "b" because Child is overriding
    // a binding method in Parent, and "c" because Child is defining a binding method that overrides
    // Parent.
    XTypeElement currentClass = subject;
    // We keep track of visited methods so we don't spam with multiple failures.
    Set<XMethodElement> visitedMethods = Sets.newHashSet();
    ListMultimap<String, XMethodElement> allMethodsByName =
        MultimapBuilder.hashKeys().arrayListValues().build(moduleMethodsByName);

    while (!currentClass.getSuperType().asTypeName().equals(XTypeName.ANY_OBJECT)) {
      currentClass = currentClass.getSuperType().getTypeElement();
      List<XMethodElement> superclassMethods = currentClass.getDeclaredMethods();
      for (XMethodElement superclassMethod : superclassMethods) {
        String name = getSimpleName(superclassMethod);
        // For each method in the superclass, confirm our binding methods don't override it
        for (XMethodElement bindingMethod : bindingMethodsByName.get(name)) {
          if (visitedMethods.add(bindingMethod)
              && bindingMethod.overrides(superclassMethod, subject)) {
            builder.addError(
                String.format(
                    "Binding methods may not override another method. Overrides: %s",
                    methodSignatureFormatter.format(superclassMethod)),
                bindingMethod);
          }
        }
        // For each binding method in superclass, confirm our methods don't override it.
        if (anyBindingMethodValidator.isBindingMethod(superclassMethod)) {
          for (XMethodElement method : allMethodsByName.get(name)) {
            if (visitedMethods.add(method) && method.overrides(superclassMethod, subject)) {
              builder.addError(
                  String.format(
                      "Binding methods may not be overridden in modules. Overrides: %s",
                      methodSignatureFormatter.format(superclassMethod)),
                  method);
            }
          }
        }
        // TODO(b/202521399): Add a test for cases that add to this map.
        allMethodsByName.put(getSimpleName(superclassMethod), superclassMethod);
      }
    }
  }

  private void validateModuleVisibility(
      XTypeElement moduleElement, ModuleKind moduleKind, ValidationReport.Builder reportBuilder) {
    if (moduleElement.isPrivate() || moduleElement.isKtPrivate()) {
      reportBuilder.addError("Modules cannot be private.", moduleElement);
    } else if (isEffectivelyPrivate(moduleElement)) {
      reportBuilder.addError("Modules cannot be enclosed in private types.", moduleElement);
    }
    if (isEffectivelyPublic(moduleElement)) {
      ImmutableSet<XTypeElement> invalidVisibilityIncludes =
          getModuleIncludesWithInvalidVisibility(moduleKind.getModuleAnnotation(moduleElement));
      if (!invalidVisibilityIncludes.isEmpty()) {
        reportBuilder.addError(
            String.format(
                "This module is public, but it includes non-public (or effectively non-public) "
                    + "modules (%s) that have non-static, non-abstract binding methods. Either "
                    + "reduce the visibility of this module, make the included modules "
                    + "public, or make all of the binding methods on the included modules "
                    + "abstract or static.",
                formatListForErrorMessage(
                    invalidVisibilityIncludes.stream()
                        .map(XTypeElement::asClassName)
                        .map(XClassName::getCanonicalName)
                        .collect(toImmutableList()))),
            moduleElement);
      }
    }
  }

  private ImmutableSet<XTypeElement> getModuleIncludesWithInvalidVisibility(
      XAnnotation moduleAnnotation) {
    return moduleAnnotation.getAnnotationValue("includes").asTypeList().stream()
        .map(XType::getTypeElement)
        .filter(include -> !isEffectivelyPublic(include))
        .filter(ComponentRequirement::requiresModuleInstance)
        .collect(toImmutableSet());
  }

  private void validateNoScopeAnnotationsOnModuleElement(
      XTypeElement module, ModuleKind moduleKind, ValidationReport.Builder report) {
    for (Scope scope : injectionAnnotations.getScopes(module)) {
      report.addError(
          String.format(
              "@%ss cannot be scoped. Did you mean to scope a method instead?",
              moduleKind.annotation().getSimpleName()),
          module,
          scope.scopeAnnotation().xprocessing());
    }
  }

  private void validateSelfCycles(
      XTypeElement module, ModuleKind moduleKind, ValidationReport.Builder builder) {
    XAnnotation moduleAnnotation = moduleKind.getModuleAnnotation(module);
    moduleAnnotation.getAsAnnotationValueList("includes").stream()
        .filter(includedModule -> areEquivalentTypes(module.getType(), includedModule.asType()))
        .forEach(
            includedModule ->
                builder.addError(
                    String.format(
                        "@%s cannot include themselves.", moduleKind.annotation().getSimpleName()),
                    module,
                    moduleAnnotation,
                    includedModule));
  }

  private void validateCompanionModule(
      XTypeElement companionModule, ValidationReport.Builder builder) {
    List<XMethodElement> companionBindingMethods = new ArrayList<>();
    for (XMethodElement companionMethod : companionModule.getDeclaredMethods()) {
      if (anyBindingMethodValidator.isBindingMethod(companionMethod)) {
        builder.addSubreport(anyBindingMethodValidator.validate(companionMethod));
        companionBindingMethods.add(companionMethod);
      }

      // On normal modules only overriding other binding methods is disallowed, but for companion
      // objects we are prohibiting any override. For this can rely on checking the @Override
      // annotation since the Kotlin compiler will always produce them for overriding methods.
      if (companionMethod.hasAnnotation(XTypeNames.OVERRIDE)) {
        builder.addError(
            "Binding method in companion object may not override another method.", companionMethod);
      }

      // TODO(danysantiago): Be strict about the usage of @JvmStatic, i.e. tell user to remove it.
    }

    ImmutableListMultimap<String, XMethodElement> bindingMethodsByName =
        Multimaps.index(companionBindingMethods, XElements::getSimpleName);
    validateMethodsWithSameName(builder, bindingMethodsByName);

    // If there are provision methods, then check the visibility. Companion objects are composed by
    // an inner class and a static field, it is not enough to check the visibility on the type
    // element or the field, therefore we check the metadata.
    if (!companionBindingMethods.isEmpty() && companionModule.isPrivate()) {
      builder.addError(
          "A Companion Module with binding methods cannot be private.", companionModule);
    }
  }

  private void validateModuleBindings(XTypeElement module, ValidationReport.Builder report) {
    BindingGraph bindingGraph =
        bindingGraphFactory
            .create(componentDescriptorFactory.moduleComponentDescriptor(module), true)
            .topLevelBindingGraph();
    if (!bindingGraphValidator.isValid(bindingGraph)) {
      // Since the validator uses a DiagnosticReporter to report errors, the ValdiationReport won't
      // have any Items for them. We have to tell the ValidationReport that some errors were
      // reported for the subject.
      report.markDirty();
    }
  }

  private static String formatListForErrorMessage(List<?> things) {
    switch (things.size()) {
      case 0:
        return "";
      case 1:
        return things.get(0).toString();
      default:
        StringBuilder output = new StringBuilder();
        Joiner.on(", ").appendTo(output, things.subList(0, things.size() - 1));
        output.append(" and ").append(things.get(things.size() - 1));
        return output.toString();
    }
  }
}
