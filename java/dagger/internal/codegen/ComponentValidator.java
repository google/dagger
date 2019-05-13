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

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Multimaps.asMap;
import static com.google.common.collect.Sets.intersection;
import static dagger.internal.codegen.ComponentAnnotation.anyComponentAnnotation;
import static dagger.internal.codegen.ComponentAnnotation.componentAnnotation;
import static dagger.internal.codegen.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.ComponentCreatorAnnotation.productionCreatorAnnotations;
import static dagger.internal.codegen.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.ComponentKind.annotationsFor;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.ErrorMessages.ComponentCreatorMessages.builderMethodRequiresNoArgs;
import static dagger.internal.codegen.ErrorMessages.ComponentCreatorMessages.moreThanOneRefToSubcomponent;
import static dagger.internal.codegen.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;
import static java.util.Comparator.comparing;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Reusable;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.producers.CancellationPolicy;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Performs superficial validation of the contract of the {@link Component} and {@link
 * ProductionComponent} annotations.
 */
final class ComponentValidator {
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final ModuleValidator moduleValidator;
  private final ComponentCreatorValidator creatorValidator;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final MembersInjectionValidator membersInjectionValidator;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFactory dependencyRequestFactory;

  @Inject
  ComponentValidator(
      DaggerElements elements,
      DaggerTypes types,
      ModuleValidator moduleValidator,
      ComponentCreatorValidator creatorValidator,
      DependencyRequestValidator dependencyRequestValidator,
      MembersInjectionValidator membersInjectionValidator,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFactory dependencyRequestFactory) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
    this.creatorValidator = creatorValidator;
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.membersInjectionValidator = membersInjectionValidator;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFactory = dependencyRequestFactory;
  }

  @AutoValue
  abstract static class ComponentValidationReport {
    abstract ImmutableSet<Element> referencedSubcomponents();

    abstract ValidationReport<TypeElement> report();
  }

  /**
   * Validates the given component subject. Also validates any referenced subcomponents that aren't
   * already included in the {@code validatedSubcomponents} set.
   */
  public ComponentValidationReport validate(
      TypeElement subject,
      Set<? extends Element> validatedSubcomponents,
      Set<? extends Element> validatedSubcomponentCreators) {
    ValidationReport.Builder<TypeElement> report = ValidationReport.about(subject);

    ImmutableSet<ComponentKind> componentKinds = ComponentKind.getComponentKinds(subject);
    ImmutableSet<Element> allSubcomponents;
    if (componentKinds.size() > 1) {
      String error =
          "Components may not be annotated with more than one component annotation: found "
              + annotationsFor(componentKinds);
      report.addError(error, subject);
      allSubcomponents = ImmutableSet.of();
    } else {
      ComponentKind componentKind = getOnlyElement(componentKinds);
      ComponentAnnotation componentAnnotation = anyComponentAnnotation(subject).get();
      allSubcomponents =
          validate(
              subject,
              componentAnnotation,
              componentKind,
              validatedSubcomponents,
              validatedSubcomponentCreators,
              report);
    }

    return new AutoValue_ComponentValidator_ComponentValidationReport(
        allSubcomponents, report.build());
  }

  private ImmutableSet<Element> validate(
      TypeElement subject,
      ComponentAnnotation componentAnnotation,
      ComponentKind componentKind,
      Set<? extends Element> validatedSubcomponents,
      Set<? extends Element> validatedSubcomponentCreators,
      ValidationReport.Builder<TypeElement> report) {
    if (isAnnotationPresent(subject, CancellationPolicy.class) && !componentKind.isProducer()) {
      report.addError(
          "@CancellationPolicy may only be applied to production components and subcomponents",
          subject);
    }

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      report.addError(
          String.format(
              "@%s may only be applied to an interface or abstract class",
              componentKind.annotation().getSimpleName()),
          subject);
    }

    ImmutableList<DeclaredType> creators =
        creatorAnnotationsFor(componentAnnotation).stream()
            .flatMap(annotation -> enclosedAnnotatedTypes(subject, annotation).stream())
            .collect(toImmutableList());
    if (creators.size() > 1) {
      report.addError(
          String.format(ErrorMessages.componentMessagesFor(componentKind).moreThanOne(), creators),
          subject);
    }

    Optional<AnnotationMirror> reusableAnnotation = getAnnotationMirror(subject, Reusable.class);
    if (reusableAnnotation.isPresent()) {
      report.addError(
          "@Reusable cannot be applied to components or subcomponents",
          subject,
          reusableAnnotation.get());
    }

    DeclaredType subjectType = MoreTypes.asDeclared(subject.asType());

    SetMultimap<Element, ExecutableElement> referencedSubcomponents = LinkedHashMultimap.create();
    getLocalAndInheritedMethods(subject, types, elements).stream()
        .filter(method -> method.getModifiers().contains(ABSTRACT))
        .forEachOrdered(
            method -> {
              ExecutableType resolvedMethod = asExecutable(types.asMemberOf(subjectType, method));
              List<? extends TypeMirror> parameterTypes = resolvedMethod.getParameterTypes();
              List<? extends VariableElement> parameters = method.getParameters();
              TypeMirror returnType = resolvedMethod.getReturnType();

              if (!resolvedMethod.getTypeVariables().isEmpty()) {
                report.addError("Component methods cannot have type variables", method);
              }

              // abstract methods are ones we have to implement, so they each need to be validated
              // first, check the return type. if it's a subcomponent, validate that method as such.
              Optional<AnnotationMirror> subcomponentAnnotation =
                  checkForAnnotations(
                      returnType,
                      componentKind.legalSubcomponentKinds().stream()
                          .map(ComponentKind::annotation)
                          .collect(toImmutableSet()));
              Optional<AnnotationMirror> subcomponentCreatorAnnotation =
                  checkForAnnotations(
                      returnType,
                      componentAnnotation.isProduction()
                          ? intersection(
                              subcomponentCreatorAnnotations(), productionCreatorAnnotations())
                          : subcomponentCreatorAnnotations());
              if (subcomponentAnnotation.isPresent()) {
                referencedSubcomponents.put(MoreTypes.asElement(returnType), method);
                validateSubcomponentMethod(
                    report,
                    ComponentKind.forAnnotatedElement(MoreTypes.asTypeElement(returnType)).get(),
                    method,
                    parameters,
                    parameterTypes,
                    returnType,
                    subcomponentAnnotation);
              } else if (subcomponentCreatorAnnotation.isPresent()) {
                referencedSubcomponents.put(
                    MoreTypes.asElement(returnType).getEnclosingElement(), method);
                validateSubcomponentCreatorMethod(
                    report, method, parameters, returnType, validatedSubcomponentCreators);
              } else {
                // if it's not a subcomponent...
                switch (parameters.size()) {
                  case 0:
                    // no parameters means that it is a provision method
                    dependencyRequestValidator.validateDependencyRequest(
                        report, method, returnType);
                    break;
                  case 1:
                    // one parameter means that it's a members injection method
                    TypeMirror parameterType = Iterables.getOnlyElement(parameterTypes);
                    report.addSubreport(
                        membersInjectionValidator.validateMembersInjectionMethod(
                            method, parameterType));
                    if (!(returnType.getKind().equals(VOID)
                        || types.isSameType(returnType, parameterType))) {
                      report.addError(
                          "Members injection methods may only return the injected type or void.",
                          method);
                    }
                    break;
                  default:
                    // this isn't any method that we know how to implement...
                    report.addError(
                        "This method isn't a valid provision method, members injection method or "
                            + "subcomponent factory method. Dagger cannot implement this method",
                        method);
                    break;
                }
              }
            });

    checkConflictingEntryPoints(report);

    Maps.filterValues(referencedSubcomponents.asMap(), methods -> methods.size() > 1)
        .forEach(
            (subcomponent, methods) ->
                report.addError(
                    String.format(moreThanOneRefToSubcomponent(), subcomponent, methods), subject));

    validateComponentDependencies(report, componentAnnotation.dependencyTypes());
    report.addSubreport(
        moduleValidator.validateReferencedModules(
            subject,
            componentAnnotation.annotation(),
            componentKind.legalModuleKinds(),
            new HashSet<>()));

    // Make sure we validate any subcomponents we're referencing, unless we know we validated
    // them already in this pass.
    // TODO(sameb): If subcomponents refer to each other and both aren't in
    //              'validatedSubcomponents' (e.g, both aren't compiled in this pass),
    //              then this can loop forever.
    ImmutableSet.Builder<Element> allSubcomponents =
        ImmutableSet.<Element>builder().addAll(referencedSubcomponents.keySet());
    for (Element subcomponent :
        Sets.difference(referencedSubcomponents.keySet(), validatedSubcomponents)) {
      ComponentValidationReport subreport =
          validate(asType(subcomponent), validatedSubcomponents, validatedSubcomponentCreators);
      report.addItems(subreport.report().items());
      allSubcomponents.addAll(subreport.referencedSubcomponents());
    }
    return allSubcomponents.build();
  }

  private void checkConflictingEntryPoints(ValidationReport.Builder<TypeElement> report) {
    DeclaredType componentType = asDeclared(report.getSubject().asType());

    // Collect entry point methods that are not overridden by others. If the "same" method is
    // inherited from more than one supertype, each will be in the multimap.
    SetMultimap<String, ExecutableElement> entryPointMethods = HashMultimap.create();

    methodsIn(elements.getAllMembers(report.getSubject()))
        .stream()
        .filter(
            method -> isEntryPoint(method, asExecutable(types.asMemberOf(componentType, method))))
        .forEach(
            method ->
                addMethodUnlessOverridden(
                    method, entryPointMethods.get(method.getSimpleName().toString())));

    for (Set<ExecutableElement> methods : asMap(entryPointMethods).values()) {
      if (distinctKeys(methods, report.getSubject()).size() > 1) {
        reportConflictingEntryPoints(methods, report);
      }
    }
  }

  private boolean isEntryPoint(ExecutableElement method, ExecutableType methodType) {
    return method.getModifiers().contains(ABSTRACT)
        && method.getParameters().isEmpty()
        && !methodType.getReturnType().getKind().equals(VOID)
        && methodType.getTypeVariables().isEmpty();
  }

  private ImmutableSet<Key> distinctKeys(Set<ExecutableElement> methods, TypeElement component) {
    return methods
        .stream()
        .map(method -> dependencyRequest(method, component))
        .map(DependencyRequest::key)
        .collect(toImmutableSet());
  }

  private DependencyRequest dependencyRequest(ExecutableElement method, TypeElement component) {
    ExecutableType methodType =
        asExecutable(types.asMemberOf(asDeclared(component.asType()), method));
    return ComponentKind.forAnnotatedElement(component).get().isProducer()
        ? dependencyRequestFactory.forComponentProductionMethod(method, methodType)
        : dependencyRequestFactory.forComponentProvisionMethod(method, methodType);
  }

  private void addMethodUnlessOverridden(ExecutableElement method, Set<ExecutableElement> methods) {
    if (methods.stream().noneMatch(existingMethod -> overridesAsDeclared(existingMethod, method))) {
      methods.removeIf(existingMethod -> overridesAsDeclared(method, existingMethod));
      methods.add(method);
    }
  }

  /**
   * Returns {@code true} if {@code overrider} overrides {@code overridden} considered from within
   * the type that declares {@code overrider}.
   */
  // TODO(dpb): Does this break for ECJ?
  private boolean overridesAsDeclared(ExecutableElement overridder, ExecutableElement overridden) {
    return elements.overrides(overridder, overridden, asType(overridder.getEnclosingElement()));
  }

  private void reportConflictingEntryPoints(
      Collection<ExecutableElement> methods, ValidationReport.Builder<TypeElement> report) {
    verify(
        methods.stream().map(ExecutableElement::getEnclosingElement).distinct().count()
            == methods.size(),
        "expected each method to be declared on a different type: %s",
        methods);
    StringBuilder message = new StringBuilder("conflicting entry point declarations:");
    methodSignatureFormatter
        .typedFormatter(asDeclared(report.getSubject().asType()))
        .formatIndentedList(
            message,
            ImmutableList.sortedCopyOf(
                comparing(
                    method -> asType(method.getEnclosingElement()).getQualifiedName().toString()),
                methods),
            1);
    report.addError(message.toString());
  }

  private void validateSubcomponentMethod(
      final ValidationReport.Builder<TypeElement> report,
      final ComponentKind subcomponentKind,
      ExecutableElement method,
      List<? extends VariableElement> parameters,
      List<? extends TypeMirror> parameterTypes,
      TypeMirror returnType,
      Optional<AnnotationMirror> subcomponentAnnotation) {
    ImmutableSet<TypeElement> moduleTypes =
        componentAnnotation(subcomponentAnnotation.get()).modules();

    // TODO(gak): This logic maybe/probably shouldn't live here as it requires us to traverse
    // subcomponents and their modules separately from how it is done in ComponentDescriptor and
    // ModuleDescriptor
    @SuppressWarnings("deprecation")
    ImmutableSet<TypeElement> transitiveModules =
        getTransitiveModules(types, elements, moduleTypes);

    Set<TypeElement> variableTypes = Sets.newHashSet();

    for (int i = 0; i < parameterTypes.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeMirror parameterType = parameterTypes.get(i);
      Optional<TypeElement> moduleType =
          parameterType.accept(
              new SimpleTypeVisitor6<Optional<TypeElement>, Void>() {
                @Override
                protected Optional<TypeElement> defaultAction(TypeMirror e, Void p) {
                  return Optional.empty();
                }

                @Override
                public Optional<TypeElement> visitDeclared(DeclaredType t, Void p) {
                  for (ModuleKind moduleKind : subcomponentKind.legalModuleKinds()) {
                    if (isAnnotationPresent(t.asElement(), moduleKind.annotation())) {
                      return Optional.of(MoreTypes.asTypeElement(t));
                    }
                  }
                  return Optional.empty();
                }
              },
              null);
      if (moduleType.isPresent()) {
        if (variableTypes.contains(moduleType.get())) {
          report.addError(
              String.format(
                  "A module may only occur once an an argument in a Subcomponent factory "
                      + "method, but %s was already passed.",
                  moduleType.get().getQualifiedName()),
              parameter);
        }
        if (!transitiveModules.contains(moduleType.get())) {
          report.addError(
              String.format(
                  "%s is present as an argument to the %s factory method, but is not one of the"
                      + " modules used to implement the subcomponent.",
                  moduleType.get().getQualifiedName(),
                  MoreTypes.asTypeElement(returnType).getQualifiedName()),
              method);
        }
        variableTypes.add(moduleType.get());
      } else {
        report.addError(
            String.format(
                "Subcomponent factory methods may only accept modules, but %s is not.",
                parameterType),
            parameter);
      }
    }
  }

  private void validateSubcomponentCreatorMethod(
      ValidationReport.Builder<TypeElement> report,
      ExecutableElement method,
      List<? extends VariableElement> parameters,
      TypeMirror returnType,
      Set<? extends Element> validatedSubcomponentCreators) {
    if (!parameters.isEmpty()) {
      report.addError(builderMethodRequiresNoArgs(), method);
    }

    // If we haven't already validated the subcomponent creator itself, validate it now.
    TypeElement creatorElement = MoreTypes.asTypeElement(returnType);
    if (!validatedSubcomponentCreators.contains(creatorElement)) {
      // TODO(sameb): The creator validator right now assumes the element is being compiled
      // in this pass, which isn't true here.  We should change error messages to spit out
      // this method as the subject and add the original subject to the message output.
      report.addItems(creatorValidator.validate(creatorElement).items());
    }
  }

  private static <T extends Element> void validateComponentDependencies(
      ValidationReport.Builder<T> report, Iterable<TypeMirror> types) {
    for (TypeMirror type : types) {
      type.accept(CHECK_DEPENDENCY_TYPES, report);
    }
  }

  private static final TypeVisitor<Void, ValidationReport.Builder<?>> CHECK_DEPENDENCY_TYPES =
      new SimpleTypeVisitor8<Void, ValidationReport.Builder<?>>() {
        @Override
        protected Void defaultAction(TypeMirror type, ValidationReport.Builder<?> report) {
          report.addError(type + " is not a valid component dependency type");
          return null;
        }

        @Override
        public Void visitDeclared(DeclaredType type, ValidationReport.Builder<?> report) {
          if (moduleAnnotation(MoreTypes.asTypeElement(type)).isPresent()) {
            report.addError(type + " is a module, which cannot be a component dependency");
          }
          return null;
        }
      };

  private static Optional<AnnotationMirror> checkForAnnotations(
      TypeMirror type, final Set<? extends Class<? extends Annotation>> annotations) {
    return type.accept(
        new SimpleTypeVisitor6<Optional<AnnotationMirror>, Void>(Optional.empty()) {
          @Override
          public Optional<AnnotationMirror> visitDeclared(DeclaredType t, Void p) {
            return getAnyAnnotation(t.asElement(), annotations);
          }
        },
        null);
  }
}
