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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreTypes.asExecutable;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedBuilders;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleAnnotation;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.ErrorMessages.COMPONENT_ANNOTATED_REUSABLE;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Reusable;
import dagger.internal.codegen.ComponentDescriptor.Kind;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Performs superficial validation of the contract of the {@link Component} and {@link
 * ProductionComponent} annotations.
 *
 * @author Gregory Kick
 */
final class ComponentValidator {
  private final Elements elements;
  private final Types types;
  private final ModuleValidator moduleValidator;
  private final ComponentValidator subcomponentValidator;
  private final BuilderValidator subcomponentBuilderValidator;

  private ComponentValidator(
      Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      BuilderValidator subcomponentBuilderValidator) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
    this.subcomponentValidator = this;
    this.subcomponentBuilderValidator = subcomponentBuilderValidator;
  }

  private ComponentValidator(
      Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      ComponentValidator subcomponentValidator,
      BuilderValidator subcomponentBuilderValidator) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
    this.subcomponentValidator = subcomponentValidator;
    this.subcomponentBuilderValidator = subcomponentBuilderValidator;
  }

  static ComponentValidator createForComponent(
      Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      ComponentValidator subcomponentValidator,
      BuilderValidator subcomponentBuilderValidator) {
    return new ComponentValidator(
        elements, types, moduleValidator, subcomponentValidator, subcomponentBuilderValidator);
  }

  static ComponentValidator createForSubcomponent(
      Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      BuilderValidator subcomponentBuilderValidator) {
    return new ComponentValidator(elements, types, moduleValidator, subcomponentBuilderValidator);
  }

  @AutoValue
  abstract static class ComponentValidationReport {
    abstract Set<Element> referencedSubcomponents();

    abstract ValidationReport<TypeElement> report();
  }

  /**
   * Validates the given component subject. Also validates any referenced subcomponents that aren't
   * already included in the {@code validatedSubcomponents} set.
   */
  public ComponentValidationReport validate(
      final TypeElement subject,
      Set<? extends Element> validatedSubcomponents,
      Set<? extends Element> validatedSubcomponentBuilders) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(subject);

    ComponentDescriptor.Kind componentKind =
        ComponentDescriptor.Kind.forAnnotatedElement(subject).get();

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      builder.addError(
          String.format(
              "@%s may only be applied to an interface or abstract class",
              componentKind.annotationType().getSimpleName()),
          subject);
    }

    ImmutableList<DeclaredType> builders =
        enclosedBuilders(subject, componentKind.builderAnnotationType());
    if (builders.size() > 1) {
      builder.addError(
          String.format(ErrorMessages.builderMsgsFor(componentKind).moreThanOne(), builders),
          subject);
    }

    Optional<AnnotationMirror> reusableAnnotation = getAnnotationMirror(subject, Reusable.class);
    if (reusableAnnotation.isPresent()) {
      builder.addError(COMPONENT_ANNOTATED_REUSABLE, subject, reusableAnnotation.get());
    }

    DeclaredType subjectType = MoreTypes.asDeclared(subject.asType());

    SetMultimap<Element, ExecutableElement> referencedSubcomponents = LinkedHashMultimap.create();
    getLocalAndInheritedMethods(subject, types, elements)
        .stream()
        .filter(method -> method.getModifiers().contains(ABSTRACT))
        .forEachOrdered(
            method -> {
              ExecutableType resolvedMethod = asExecutable(types.asMemberOf(subjectType, method));
              List<? extends TypeMirror> parameterTypes = resolvedMethod.getParameterTypes();
              List<? extends VariableElement> parameters = method.getParameters();
              TypeMirror returnType = resolvedMethod.getReturnType();

              if (!resolvedMethod.getTypeVariables().isEmpty()) {
                builder.addError("Component methods cannot have type variables", method);
              }

              // abstract methods are ones we have to implement, so they each need to be validated
              // first, check the return type. if it's a subcomponent, validate that method as such.
              Optional<AnnotationMirror> subcomponentAnnotation =
                  checkForAnnotations(
                      returnType,
                      FluentIterable.from(componentKind.subcomponentKinds())
                          .transform(Kind::annotationType)
                          .toSet());
              Optional<AnnotationMirror> subcomponentBuilderAnnotation =
                  checkForAnnotations(
                      returnType,
                      FluentIterable.from(componentKind.subcomponentKinds())
                          .transform(Kind::builderAnnotationType)
                          .toSet());
              if (subcomponentAnnotation.isPresent()) {
                referencedSubcomponents.put(MoreTypes.asElement(returnType), method);
                validateSubcomponentMethod(
                    builder,
                    ComponentDescriptor.Kind.forAnnotatedElement(
                            MoreTypes.asTypeElement(returnType))
                        .get(),
                    method,
                    parameters,
                    parameterTypes,
                    returnType,
                    subcomponentAnnotation);
              } else if (subcomponentBuilderAnnotation.isPresent()) {
                referencedSubcomponents.put(
                    MoreTypes.asElement(returnType).getEnclosingElement(), method);
                validateSubcomponentBuilderMethod(
                    builder, method, parameters, returnType, validatedSubcomponentBuilders);
              } else {
                // if it's not a subcomponent...
                switch (parameters.size()) {
                  case 0:
                    // no parameters means that it is a provision method
                    // basically, there are no restrictions here.  \o/
                    break;
                  case 1:
                    // one parameter means that it's a members injection method
                    TypeMirror onlyParameter = Iterables.getOnlyElement(parameterTypes);
                    if (!(returnType.getKind().equals(VOID)
                        || types.isSameType(returnType, onlyParameter))) {
                      builder.addError(
                          "Members injection methods may only return the injected type or void.",
                          method);
                    }
                    break;
                  default:
                    // this isn't any method that we know how to implement...
                    builder.addError(
                        "This method isn't a valid provision method, members injection method or "
                            + "subcomponent factory method. Dagger cannot implement this method",
                        method);
                    break;
                }
              }
            });

    Maps.filterValues(referencedSubcomponents.asMap(), methods -> methods.size() > 1)
        .forEach(
            (subcomponent, methods) ->
                builder.addError(
                    String.format(
                        ErrorMessages.SubcomponentBuilderMessages.INSTANCE
                            .moreThanOneRefToSubcomponent(),
                        subcomponent,
                        methods),
                    subject));

    AnnotationMirror componentMirror =
        getAnnotationMirror(subject, componentKind.annotationType()).get();
    if (componentKind.isTopLevel()) {
      validateComponentDependencies(builder, getComponentDependencies(componentMirror));
    }
    builder.addSubreport(
        moduleValidator.validateReferencedModules(
            subject, componentMirror, componentKind.moduleKinds()));

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
          subcomponentValidator.validate(
              MoreElements.asType(subcomponent),
              validatedSubcomponents,
              validatedSubcomponentBuilders);
      builder.addItems(subreport.report().items());
      allSubcomponents.addAll(subreport.referencedSubcomponents());
    }

    return new AutoValue_ComponentValidator_ComponentValidationReport(
        allSubcomponents.build(), builder.build());
  }

  private void validateSubcomponentMethod(
      final ValidationReport.Builder<TypeElement> builder,
      final ComponentDescriptor.Kind subcomponentKind,
      ExecutableElement method,
      List<? extends VariableElement> parameters,
      List<? extends TypeMirror> parameterTypes,
      TypeMirror returnType,
      Optional<AnnotationMirror> subcomponentAnnotation) {
    ImmutableSet<TypeElement> moduleTypes =
        MoreTypes.asTypeElements(getComponentModules(subcomponentAnnotation.get()));

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
                  for (ModuleDescriptor.Kind moduleKind : subcomponentKind.moduleKinds()) {
                    if (MoreElements.isAnnotationPresent(
                        t.asElement(), moduleKind.moduleAnnotation())) {
                      return Optional.of(MoreTypes.asTypeElement(t));
                    }
                  }
                  return Optional.empty();
                }
              },
              null);
      if (moduleType.isPresent()) {
        if (variableTypes.contains(moduleType.get())) {
          builder.addError(
              String.format(
                  "A module may only occur once an an argument in a Subcomponent factory "
                      + "method, but %s was already passed.",
                  moduleType.get().getQualifiedName()),
              parameter);
        }
        if (!transitiveModules.contains(moduleType.get())) {
          builder.addError(
              String.format(
                  "%s is present as an argument to the %s factory method, but is not one of the"
                      + " modules used to implement the subcomponent.",
                  moduleType.get().getQualifiedName(),
                  MoreTypes.asTypeElement(returnType).getQualifiedName()),
              method);
        }
        variableTypes.add(moduleType.get());
      } else {
        builder.addError(
            String.format(
                "Subcomponent factory methods may only accept modules, but %s is not.",
                parameterType),
            parameter);
      }
    }
  }

  private void validateSubcomponentBuilderMethod(
      ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method,
      List<? extends VariableElement> parameters,
      TypeMirror returnType,
      Set<? extends Element> validatedSubcomponentBuilders) {

    if (!parameters.isEmpty()) {
      builder.addError(
          ErrorMessages.SubcomponentBuilderMessages.INSTANCE.builderMethodRequiresNoArgs(), method);
    }

    // If we haven't already validated the subcomponent builder itself, validate it now.
    TypeElement builderElement = MoreTypes.asTypeElement(returnType);
    if (!validatedSubcomponentBuilders.contains(builderElement)) {
      // TODO(sameb): The builder validator right now assumes the element is being compiled
      // in this pass, which isn't true here.  We should change error messages to spit out
      // this method as the subject and add the original subject to the message output.
      builder.addItems(subcomponentBuilderValidator.validate(builderElement).items());
    }
  }

  private static <T extends Element> void validateComponentDependencies(
      ValidationReport.Builder<T> report, Iterable<TypeMirror> types) {
    validateTypesAreDeclared(report, types, "component dependency");
    for (TypeMirror type : types) {
      if (getModuleAnnotation(MoreTypes.asTypeElement(type)).isPresent()) {
        report.addError(
            String.format("%s is a module, which cannot be a component dependency", type));
      }
    }
  }

  private static <T extends Element> void validateTypesAreDeclared(
      final ValidationReport.Builder<T> report, Iterable<TypeMirror> types, final String typeName) {
    for (TypeMirror type : types) {
      type.accept(
          new SimpleTypeVisitor6<Void, Void>() {
            @Override
            protected Void defaultAction(TypeMirror e, Void aVoid) {
              report.addError(String.format("%s is not a valid %s type", e, typeName));
              return null;
            }

            @Override
            public Void visitDeclared(DeclaredType t, Void aVoid) {
              // Declared types are valid
              return null;
            }
          },
          null);
    }
  }

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
