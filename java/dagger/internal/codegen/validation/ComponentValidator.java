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

import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.consumingIterable;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Multimaps.asMap;
import static com.google.common.collect.Sets.intersection;
import static dagger.internal.codegen.base.ComponentAnnotation.anyComponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.productionCreatorAnnotations;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.base.ComponentKind.annotationsFor;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotations;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages.builderMethodRequiresNoArgs;
import static dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages.moreThanOneRefToSubcomponent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.Comparator.comparing;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.Component;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.ComponentKind;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.binding.DependencyRequestFactory;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.xprocessing.XTypeElements;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;

/**
 * Performs superficial validation of the contract of the {@link Component} and {@link
 * dagger.producers.ProductionComponent} annotations.
 */
@Singleton
public final class ComponentValidator implements ClearableCache {
  private final DaggerElements elements;
  private final ModuleValidator moduleValidator;
  private final ComponentCreatorValidator creatorValidator;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final MembersInjectionValidator membersInjectionValidator;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final DaggerSuperficialValidation superficialValidation;
  private final Map<XTypeElement, ValidationReport> reports = new HashMap<>();
  private final KotlinMetadataUtil metadataUtil;

  @Inject
  ComponentValidator(
      DaggerElements elements,
      ModuleValidator moduleValidator,
      ComponentCreatorValidator creatorValidator,
      DependencyRequestValidator dependencyRequestValidator,
      MembersInjectionValidator membersInjectionValidator,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFactory dependencyRequestFactory,
      DaggerSuperficialValidation superficialValidation,
      KotlinMetadataUtil metadataUtil) {
    this.elements = elements;
    this.moduleValidator = moduleValidator;
    this.creatorValidator = creatorValidator;
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.membersInjectionValidator = membersInjectionValidator;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.superficialValidation = superficialValidation;
    this.metadataUtil = metadataUtil;
  }

  @Override
  public void clearCache() {
    reports.clear();
  }

  /** Validates the given component. */
  public ValidationReport validate(XTypeElement component) {
    return reentrantComputeIfAbsent(reports, component, this::validateUncached);
  }

  private ValidationReport validateUncached(XTypeElement component) {
    return new ElementValidator(component).validateElement();
  }

  private class ElementValidator {
    private final XTypeElement component;
    private final ValidationReport.Builder report;
    private final ImmutableSet<ComponentKind> componentKinds;

    // Populated by ComponentMethodValidators
    private final SetMultimap<XTypeElement, XMethodElement> referencedSubcomponents =
        LinkedHashMultimap.create();

    ElementValidator(XTypeElement component) {
      this.component = component;
      this.report = ValidationReport.about(component);
      this.componentKinds = ComponentKind.getComponentKinds(component);
    }

    private ComponentKind componentKind() {
      return getOnlyElement(componentKinds);
    }

    private ComponentAnnotation componentAnnotation() {
      return anyComponentAnnotation(component, superficialValidation).get();
    }

    ValidationReport validateElement() {
      if (componentKinds.size() > 1) {
        return moreThanOneComponentAnnotation();
      }

      validateUseOfCancellationPolicy();
      validateIsAbstractType();
      validateCreators();
      validateNoReusableAnnotation();
      validateComponentMethods();
      validateNoConflictingEntryPoints();
      validateSubcomponentReferences();
      validateComponentDependencies();
      validateReferencedModules();
      validateSubcomponents();

      return report.build();
    }

    private ValidationReport moreThanOneComponentAnnotation() {
      String error =
          "Components may not be annotated with more than one component annotation: found "
              + annotationsFor(componentKinds);
      report.addError(error, component);
      return report.build();
    }

    private void validateUseOfCancellationPolicy() {
      if (component.hasAnnotation(TypeNames.CANCELLATION_POLICY) && !componentKind().isProducer()) {
        report.addError(
            "@CancellationPolicy may only be applied to production components and subcomponents",
            component);
      }
    }

    private void validateIsAbstractType() {
      if (!component.isInterface() && !(component.isClass() && component.isAbstract())) {
        report.addError(
            String.format(
                "@%s may only be applied to an interface or abstract class",
                componentKind().annotation().simpleName()),
            component);
      }
    }

    private void validateCreators() {
      ImmutableSet<XTypeElement> creators =
          enclosedAnnotatedTypes(component, creatorAnnotationsFor(componentAnnotation()));
      creators.forEach(creator -> report.addSubreport(creatorValidator.validate(creator)));
      if (creators.size() > 1) {
        report.addError(
            String.format(
                ErrorMessages.componentMessagesFor(componentKind()).moreThanOne(), creators),
            component);
      }
    }

    private void validateNoReusableAnnotation() {
      if (component.hasAnnotation(TypeNames.REUSABLE)) {
        report.addError(
            "@Reusable cannot be applied to components or subcomponents",
            component,
            component.getAnnotation(TypeNames.REUSABLE));
      }
    }

    private void validateComponentMethods() {
      validateClassMethodName();
      getAllUnimplementedMethods(component).stream()
          .map(ComponentMethodValidator::new)
          .forEachOrdered(ComponentMethodValidator::validateMethod);
    }

    private void validateClassMethodName() {
      if (metadataUtil.hasMetadata(toJavac(component))) {
        metadataUtil
            .getAllMethodNamesBySignature(toJavac(component))
            .forEach(
                (signature, name) -> {
                  if (SourceVersion.isKeyword(name)) {
                    report.addError("Can not use a Java keyword as method name: " + signature);
                  }
                });
      }
    }

    private class ComponentMethodValidator {
      private final XMethodElement method;
      private final XMethodType resolvedMethod;
      private final List<XType> parameterTypes;
      private final List<XExecutableParameterElement> parameters;
      private final XType returnType;

      ComponentMethodValidator(XMethodElement method) {
        this.method = method;
        this.resolvedMethod = method.asMemberOf(component.getType());
        this.parameterTypes = resolvedMethod.getParameterTypes();
        this.parameters = method.getParameters();
        this.returnType = resolvedMethod.getReturnType();
      }

      void validateMethod() {
        validateNoTypeVariables();

        // abstract methods are ones we have to implement, so they each need to be validated
        // first, check the return type. if it's a subcomponent, validate that method as
        // such.
        Optional<ComponentAnnotation> subcomponentAnnotation = legalSubcomponentAnnotation();
        if (subcomponentAnnotation.isPresent()) {
          validateSubcomponentFactoryMethod(subcomponentAnnotation.get());
        } else if (subcomponentCreatorAnnotation().isPresent()) {
          validateSubcomponentCreatorMethod();
        } else {
          // if it's not a subcomponent...
          switch (parameters.size()) {
            case 0:
              validateProvisionMethod();
              break;
            case 1:
              validateMembersInjectionMethod();
              break;
            default:
              reportInvalidMethod();
              break;
          }
        }
      }

      private void validateNoTypeVariables() {
        if (!resolvedMethod.getTypeVariableNames().isEmpty()) {
          report.addError("Component methods cannot have type variables", method);
        }
      }

      private Optional<ComponentAnnotation> legalSubcomponentAnnotation() {
        return Optional.ofNullable(returnType.getTypeElement())
            .flatMap(element -> subcomponentAnnotation(element, superficialValidation))
            // TODO(bcorso): Consider failing on illegal subcomponents rather than just filtering.
            .filter(annotation -> legalSubcomponentAnnotations().contains(annotation.className()));
      }

      private ImmutableSet<ClassName> legalSubcomponentAnnotations() {
        return componentKind().legalSubcomponentKinds().stream()
            .map(ComponentKind::annotation)
            .collect(toImmutableSet());
      }

      private Optional<XAnnotation> subcomponentCreatorAnnotation() {
        return checkForAnnotations(
            returnType,
            componentAnnotation().isProduction()
                ? intersection(subcomponentCreatorAnnotations(), productionCreatorAnnotations())
                : subcomponentCreatorAnnotations());
      }

      private void validateSubcomponentFactoryMethod(ComponentAnnotation subcomponentAnnotation) {
        referencedSubcomponents.put(returnType.getTypeElement(), method);

        ImmutableSet<ClassName> legalModuleAnnotations =
            ComponentKind.forAnnotatedElement(returnType.getTypeElement())
                .get()
                .legalModuleKinds()
                .stream()
                .map(ModuleKind::annotation)
                .collect(toImmutableSet());
        ImmutableSet<XTypeElement> moduleTypes = subcomponentAnnotation.modules();

        // TODO(gak): This logic maybe/probably shouldn't live here as it requires us to traverse
        // subcomponents and their modules separately from how it is done in ComponentDescriptor and
        // ModuleDescriptor
        ImmutableSet<XTypeElement> transitiveModules = getTransitiveModules(moduleTypes);

        Set<XTypeElement> referencedModules = Sets.newHashSet();
        for (int i = 0; i < parameterTypes.size(); i++) {
          XExecutableParameterElement parameter = parameters.get(i);
          XType parameterType = parameterTypes.get(i);
          if (checkForAnnotations(parameterType, legalModuleAnnotations).isPresent()) {
            XTypeElement module = parameterType.getTypeElement();
            if (referencedModules.contains(module)) {
              report.addError(
                  String.format(
                      "A module may only occur once as an argument in a Subcomponent factory "
                          + "method, but %s was already passed.",
                      module.getQualifiedName()),
                  parameter);
            }
            if (!transitiveModules.contains(module)) {
              report.addError(
                  String.format(
                      "%s is present as an argument to the %s factory method, but is not one of the"
                          + " modules used to implement the subcomponent.",
                      module.getQualifiedName(), returnType.getTypeElement().getQualifiedName()),
                  method);
            }
            referencedModules.add(module);
          } else {
            report.addError(
                String.format(
                    "Subcomponent factory methods may only accept modules, but %s is not.",
                    parameterType),
                parameter);
          }
        }
      }

      /**
       * Returns the full set of modules transitively included from the given seed modules, which
       * includes all transitive {@link Module#includes} and all transitive super classes. If a
       * module is malformed and a type listed in {@link Module#includes} is not annotated with
       * {@link Module}, it is ignored.
       */
      private ImmutableSet<XTypeElement> getTransitiveModules(
          Collection<XTypeElement> seedModules) {
        Set<XTypeElement> processedElements = Sets.newLinkedHashSet();
        Queue<XTypeElement> moduleQueue = new ArrayDeque<>(seedModules);
        ImmutableSet.Builder<XTypeElement> moduleElements = ImmutableSet.builder();
        for (XTypeElement moduleElement : consumingIterable(moduleQueue)) {
          if (processedElements.add(moduleElement)) {
            moduleAnnotation(moduleElement, superficialValidation)
                .ifPresent(
                    moduleAnnotation -> {
                      moduleElements.add(moduleElement);
                      moduleQueue.addAll(moduleAnnotation.includes());
                      moduleQueue.addAll(includesFromSuperclasses(moduleElement));
                    });
          }
        }
        return moduleElements.build();
      }

      /** Returns {@link Module#includes()} from all transitive super classes. */
      private ImmutableSet<XTypeElement> includesFromSuperclasses(XTypeElement element) {
        ImmutableSet.Builder<XTypeElement> builder = ImmutableSet.builder();
        XType superclass = element.getSuperType();
        while (superclass != null && !TypeName.OBJECT.equals(superclass.getTypeName())) {
          element = superclass.getTypeElement();
          moduleAnnotation(element, superficialValidation)
              .ifPresent(moduleAnnotation -> builder.addAll(moduleAnnotation.includes()));
          superclass = element.getSuperType();
        }
        return builder.build();
      }

      private void validateSubcomponentCreatorMethod() {
        referencedSubcomponents.put(returnType.getTypeElement().getEnclosingTypeElement(), method);

        if (!parameters.isEmpty()) {
          report.addError(builderMethodRequiresNoArgs(), method);
        }

        XTypeElement creatorElement = returnType.getTypeElement();
        // TODO(sameb): The creator validator right now assumes the element is being compiled
        // in this pass, which isn't true here.  We should change error messages to spit out
        // this method as the subject and add the original subject to the message output.
        report.addSubreport(creatorValidator.validate(creatorElement));
      }

      private void validateProvisionMethod() {
        dependencyRequestValidator.validateDependencyRequest(report, method, returnType);
      }

      private void validateMembersInjectionMethod() {
        XType parameterType = getOnlyElement(parameterTypes);
        report.addSubreport(
            membersInjectionValidator.validateMembersInjectionMethod(method, parameterType));
        if (!(isVoid(returnType) || returnType.isSameType(parameterType))) {
          report.addError(
              "Members injection methods may only return the injected type or void.", method);
        }
      }

      private void reportInvalidMethod() {
        report.addError(
            "This method isn't a valid provision method, members injection method or "
                + "subcomponent factory method. Dagger cannot implement this method",
            method);
      }
    }

    private void validateNoConflictingEntryPoints() {
      // Collect entry point methods that are not overridden by others. If the "same" method is
      // inherited from more than one supertype, each will be in the multimap.
      SetMultimap<String, XMethodElement> entryPoints = HashMultimap.create();
      XTypeElements.getAllMethods(component).stream()
          .filter(method -> isEntryPoint(method, method.asMemberOf(component.getType())))
          .forEach(
              method -> addMethodUnlessOverridden(method, entryPoints.get(getSimpleName(method))));

      asMap(entryPoints).values().stream()
          .filter(methods -> distinctKeys(methods).size() > 1)
          .forEach(this::reportConflictingEntryPoints);
    }

    private void reportConflictingEntryPoints(Collection<XMethodElement> methods) {
      verify(
          methods.stream().map(XMethodElement::getEnclosingElement).distinct().count()
              == methods.size(),
          "expected each method to be declared on a different type: %s",
          methods);
      StringBuilder message = new StringBuilder("conflicting entry point declarations:");
      methodSignatureFormatter
          .typedFormatter(component.getType())
          .formatIndentedList(
              message,
              ImmutableList.sortedCopyOf(
                  comparing(method -> method.getEnclosingElement().getClassName().canonicalName()),
                  methods),
              1);
      report.addError(message.toString());
    }

    private void validateSubcomponentReferences() {
      Maps.filterValues(referencedSubcomponents.asMap(), methods -> methods.size() > 1)
          .forEach(
              (subcomponent, methods) ->
                  report.addError(
                      String.format(moreThanOneRefToSubcomponent(), subcomponent, methods),
                      component));
    }

    private void validateComponentDependencies() {
      for (XType type : componentAnnotation().dependencyTypes()) {
        if (!isDeclared(type)) {
          report.addError(type + " is not a valid component dependency type");
        } else if (type.getTypeElement().hasAnyAnnotation(moduleAnnotations())) {
          report.addError(type + " is a module, which cannot be a component dependency");
        }
      }
    }

    private void validateReferencedModules() {
      report.addSubreport(
          moduleValidator.validateReferencedModules(
              component,
              componentAnnotation().annotation(),
              componentKind().legalModuleKinds(),
              new HashSet<>()));
    }

    private void validateSubcomponents() {
      // Make sure we validate any subcomponents we're referencing.
      referencedSubcomponents
          .keySet()
          .forEach(subcomponent -> report.addSubreport(validate(subcomponent)));
    }

    private ImmutableSet<Key> distinctKeys(Set<XMethodElement> methods) {
      return methods.stream()
          .map(this::dependencyRequest)
          .map(DependencyRequest::key)
          .collect(toImmutableSet());
    }

    private DependencyRequest dependencyRequest(XMethodElement method) {
      XMethodType methodType = method.asMemberOf(component.getType());
      return componentKind().isProducer()
          ? dependencyRequestFactory.forComponentProductionMethod(method, methodType)
          : dependencyRequestFactory.forComponentProvisionMethod(method, methodType);
    }
  }

  private static boolean isEntryPoint(XMethodElement method, XMethodType methodType) {
    return method.isAbstract()
        && method.getParameters().isEmpty()
        && !isVoid(methodType.getReturnType())
        && methodType.getTypeVariableNames().isEmpty();
  }

  private void addMethodUnlessOverridden(XMethodElement method, Set<XMethodElement> methods) {
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
  private boolean overridesAsDeclared(XMethodElement overrider, XMethodElement overridden) {
    return elements.overrides(
        toJavac(overrider),
        toJavac(overridden),
        toJavac(asTypeElement(overrider.getEnclosingElement())));
  }

  private static Optional<XAnnotation> checkForAnnotations(XType type, Set<ClassName> annotations) {
    return Optional.ofNullable(type.getTypeElement())
        .flatMap(typeElement -> getAnyAnnotation(typeElement, annotations));
  }
}
