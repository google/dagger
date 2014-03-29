/*
 * Copyright (C) 2014 Google, Inc.
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

import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_GENERIC_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_INTO_PRIVATE_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_SCOPES;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static javax.lang.model.SourceVersion.RELEASE_6;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@SupportedSourceVersion(RELEASE_6)
public final class InjectProcessor extends AbstractProcessor {
  private Messager messager;
  private MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private MembersInjectorWriter membersInjectorWriter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    Elements elements = processingEnv.getElementUtils();
    Types types = processingEnv.getTypeUtils();
    this.membersInjectionBindingFactory = new MembersInjectionBinding.Factory(
        new DependencyRequest.Factory(elements, types));
    this.membersInjectorWriter = new MembersInjectorWriter(processingEnv.getFiler(),
        new ProviderTypeRepository(elements, types));
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(Inject.class.getName());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // TODO(gak): add some error handling for bad source files
    final ImmutableSet.Builder<MembersInjectionBinding> membersInjections = ImmutableSet.builder();

    for (Element injectElement : roundEnv.getElementsAnnotatedWith(Inject.class)) {
      injectElement.accept(
          new ElementKindVisitor6<Void, Void>() {
            @Override
            public Void visitExecutableAsConstructor(ExecutableElement constructorElement, Void v) {
              ValidationReport<ExecutableElement> report =
                  validateInjectConstructor(constructorElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                // collect bindings for generating factories
              }

              return null;
            }

            @Override
            public Void visitVariableAsField(VariableElement fieldElement, Void p) {
              ValidationReport<VariableElement> report = validateInjectField(fieldElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjections.add(
                    membersInjectionBindingFactory.forInjectField(fieldElement));
              }

              return null;
            }

            @Override
            public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
              ValidationReport<ExecutableElement> report = validateInjectMethod(methodElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjections.add(
                    membersInjectionBindingFactory.forInjectMethod(methodElement));
              }

              return null;
            }
          }, null);
    }

    ImmutableListMultimap<TypeElement, MembersInjectionBinding> membersInjectionsByType =
        Multimaps.index(membersInjections.build(),
            new Function<MembersInjectionBinding, TypeElement>() {
              @Override public TypeElement apply(MembersInjectionBinding binding) {
                return binding.targetEnclosingType();
              }
            });

    for (Collection<MembersInjectionBinding> bindings : membersInjectionsByType.asMap().values()) {
      try {
        membersInjectorWriter.generate(MembersInjectorDescriptor.create(bindings));
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    return false;
  }

  private ValidationReport<ExecutableElement> validateInjectConstructor(
      ExecutableElement constructorElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(constructorElement);
    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addItem(INJECT_ON_PRIVATE_CONSTRUCTOR, constructorElement);
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addItem(MULTIPLE_QUALIFIERS, constructorElement, qualifier);
        }
      }
    }

    TypeElement enclosingElement =
        ElementUtil.asTypeElement(constructorElement.getEnclosingElement());
    Set<Modifier> typeModifiers = enclosingElement.getModifiers();

    if (typeModifiers.contains(PRIVATE)) {
      builder.addItem(INJECT_INTO_PRIVATE_CLASS, constructorElement);
    }

    if (typeModifiers.contains(ABSTRACT)) {
      builder.addItem(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS, constructorElement);
    }

    if (!enclosingElement.getTypeParameters().isEmpty()) {
      builder.addItem(INJECT_CONSTRUCTOR_ON_GENERIC_CLASS, constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addItem(INJECT_CONSTRUCTOR_ON_INNER_CLASS, constructorElement);
    }

    // This is computationally expensive, but probably preferable to a giant index
    FluentIterable<ExecutableElement> injectConstructors = FluentIterable.from(
        ElementFilter.constructorsIn(enclosingElement.getEnclosedElements()))
            .filter(new Predicate<ExecutableElement>() {
              @Override public boolean apply(ExecutableElement input) {
                return input.getAnnotation(Inject.class) != null;
              }
            });

    if (injectConstructors.size() > 1) {
      builder.addItem(MULTIPLE_INJECT_CONSTRUCTORS, constructorElement);
    }

    ImmutableSet<? extends AnnotationMirror> scopes = getScopes(enclosingElement);
    if (scopes.size() > 1) {
      for (AnnotationMirror scope : scopes) {
        builder.addItem(MULTIPLE_SCOPES, enclosingElement, scope);
      }
    }

    return builder.build();
  }

  private ValidationReport<ExecutableElement> validateInjectMethod(
      ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addItem(ABSTRACT_INJECT_METHOD, methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_METHOD, methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addItem(GENERIC_INJECT_METHOD, methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addItem(MULTIPLE_QUALIFIERS, methodElement, qualifier);
        }
      }
    }

    return builder.build();
  }

  private ValidationReport<VariableElement> validateInjectField(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder =
        ValidationReport.Builder.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addItem(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_FIELD, fieldElement);
    }

    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(fieldElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addItem(MULTIPLE_QUALIFIERS, fieldElement, qualifier);
      }
    }

    return builder.build();
  }

  private ImmutableSet<? extends AnnotationMirror> getQualifiers(Element element) {
    return getAnnotatedAnnotations(element, Qualifier.class);
  }

  private ImmutableSet<? extends AnnotationMirror> getScopes(Element element) {
    return getAnnotatedAnnotations(element, Scope.class);
  }

  private ImmutableSet<? extends AnnotationMirror> getAnnotatedAnnotations(Element element,
      final Class<? extends Annotation> annotationType) {
    List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();
    return FluentIterable.from(annotations)
        .filter(new Predicate<AnnotationMirror>() {
          @Override public boolean apply(AnnotationMirror input) {
            return input.getAnnotationType().asElement().getAnnotation(annotationType) != null;
          }
        })
        .toSet();
  }
}
