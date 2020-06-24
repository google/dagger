/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.aggregateddeps;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.asType;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.hilt.android.processor.internal.androidentrypoint.HiltCompilerOptions.BooleanOption.DISABLE_MODULES_HAVE_INSTALL_IN_CHECK;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.Components;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dummy files to propagate information through multiple javac runs. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class AggregatedDepsProcessor extends BaseProcessor {
  private static final ImmutableSet<ClassName> ALLOWED_MODULES_WITH_PARAMS =
      ImmutableSet.of(
          ClassName.get("dagger.hilt.android.internal.modules", "ApplicationContextModule"));

  private static final ImmutableSet<ClassName> INSTALL_IN_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .add(ClassNames.INSTALL_IN)
          .build();

  private static final ImmutableSet<ClassName> ENTRY_POINT_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ENTRY_POINT,
          ClassNames.GENERATED_ENTRY_POINT,
          ClassNames.COMPONENT_ENTRY_POINT);

  private final Set<Element> seen = new HashSet<>();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.builder()
        .add(ClassNames.MODULE)
        .addAll(INSTALL_IN_ANNOTATIONS)
        .addAll(ENTRY_POINT_ANNOTATIONS)
        .build()
        .stream()
        .map(Object::toString)
        .collect(toImmutableSet());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    if (!seen.add(element)) {
      return;
    }

    ImmutableSet<ClassName> installInAnnotations =
        INSTALL_IN_ANNOTATIONS.stream()
            .filter(installIn -> Processors.hasAnnotation(element, installIn))
            .collect(toImmutableSet());

    ImmutableSet<ClassName> entryPointAnnotations =
        ENTRY_POINT_ANNOTATIONS.stream()
            .filter(entryPoint -> Processors.hasAnnotation(element, entryPoint))
            .collect(toImmutableSet());
    ProcessorErrors.checkState(
        entryPointAnnotations.size() <= 1,
        element,
        "Found multiple @EntryPoint annotations on %s: %s",
        element,
        entryPointAnnotations);

    boolean hasInstallIn = !installInAnnotations.isEmpty();
    boolean isEntryPoint = !entryPointAnnotations.isEmpty();
    boolean isModule = Processors.hasAnnotation(element, ClassNames.MODULE);

    ProcessorErrors.checkState(
        !hasInstallIn || isEntryPoint || isModule,
        element,
        "@InstallIn can only be used on @Module or @EntryPoint classes: %s",
        element);

    ProcessorErrors.checkState(
        isModule != isEntryPoint,
        element,
        "@Module and @EntryPoint cannot be used on the same interface");

    ProcessorErrors.checkState(
        !isModule
            || hasInstallIn
            || isDaggerGeneratedModule(element)
            || installInCheckDisabled(element),
        element,
        "%s is missing an @InstallIn annotation. If this was intentional, see "
        + "https://dagger.dev/hilt/compiler-options#disable-install-in-check for how to disable this check.",
        element);

    if (isModule && hasInstallIn) {
      ProcessorErrors.checkState(
          element.getKind() == CLASS || element.getKind() == INTERFACE,
          element,
          "Only classes and interfaces can be annotated with @Module: %s",
          element);
      TypeElement module = asType(element);

      if (!ALLOWED_MODULES_WITH_PARAMS.contains(ClassName.get(module))) {
        ImmutableList<ExecutableElement> constructorsWithParams =
            ElementFilter.constructorsIn(module.getEnclosedElements()).stream()
                .filter(constructor -> !constructor.getParameters().isEmpty())
                .collect(toImmutableList());
        ProcessorErrors.checkState(
            constructorsWithParams.isEmpty(),
            module,
            "@InstallIn modules cannot have constructors with parameters. Found: %s",
            constructorsWithParams);
      }

      ProcessorErrors.checkState(
          Processors.isTopLevel(module)
              || module.getModifiers().contains(STATIC)
              || module.getModifiers().contains(ABSTRACT)
              || Processors.hasAnnotation(
                  module.getEnclosingElement(), ClassNames.HILT_ANDROID_TEST),
          module,
          "Nested @InstallIn modules must be static unless they are directly nested within a test. "
              + "Found: %s",
          module);

      // TODO(b/28989613): This should really be fixed in Dagger. Remove once Dagger bug is fixed.
      ImmutableList<ExecutableElement> abstractMethodsWithMissingBinds =
          ElementFilter.methodsIn(module.getEnclosedElements()).stream()
              .filter(method -> method.getModifiers().contains(ABSTRACT))
              .filter(method -> !Processors.hasDaggerAbstractMethodAnnotation(method))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          abstractMethodsWithMissingBinds.isEmpty(),
          module,
          "Found unimplemented abstract methods, %s, in an abstract module, %s. "
              + "Did you forget to add a Dagger binding annotation (e.g. @Binds)?",
          abstractMethodsWithMissingBinds,
          module);

      // Get @InstallIn components here to catch errors before skipping user's pkg-private element.
      ImmutableSet<ClassName> components = installInComponents(module);
      if (isValidKind(module)) {
        Optional<PkgPrivateMetadata> pkgPrivateMetadata =
            PkgPrivateMetadata.of(getElementUtils(), module, ClassNames.MODULE);
        if (pkgPrivateMetadata.isPresent()) {
          // Generate a public wrapper module which will be processed in the next round.
          new PkgPrivateModuleGenerator(getProcessingEnv(), pkgPrivateMetadata.get()).generate();
        } else {
          new AggregatedDepsGenerator("modules", module, components, getProcessingEnv()).generate();
        }
      }
    }

    if (isEntryPoint) {
      ClassName entryPointAnnotation = Iterables.getOnlyElement(entryPointAnnotations);

      ProcessorErrors.checkState(
          hasInstallIn ,
          element,
          "@%s %s must also be annotated with @InstallIn",
          entryPointAnnotation.simpleName(),
          element);

      ProcessorErrors.checkState(
          element.getKind() == INTERFACE,
          element,
          "Only interfaces can be annotated with @%s: %s",
          entryPointAnnotation.simpleName(),
          element);
      TypeElement entryPoint = asType(element);

      // Get @InstallIn components here to catch errors before skipping user's pkg-private element.
      ImmutableSet<ClassName> components = installInComponents(entryPoint);
      if (isValidKind(element)) {
        if (entryPointAnnotation.equals(ClassNames.COMPONENT_ENTRY_POINT)) {
          new AggregatedDepsGenerator(
                  "componentEntryPoints", entryPoint, components, getProcessingEnv())
              .generate();
        } else {
          Optional<PkgPrivateMetadata> pkgPrivateMetadata =
              PkgPrivateMetadata.of(getElementUtils(), entryPoint, entryPointAnnotation);
          if (pkgPrivateMetadata.isPresent()) {
            new PkgPrivateEntryPointGenerator(getProcessingEnv(), pkgPrivateMetadata.get())
                .generate();
          } else {
            new AggregatedDepsGenerator("entryPoints", entryPoint, components, getProcessingEnv())
                .generate();
          }
        }
      }
    }
  }

  private ImmutableSet<ClassName> installInComponents(Element element) {
    return Components.getComponentDescriptors(getElementUtils(), element).stream()
        .map(ComponentDescriptor::component)
        .collect(toImmutableSet());
  }

  private static boolean isValidKind(Element element) {
    // don't go down the rabbit hole of analyzing undefined types. N.B. we don't issue
    // an error here because javac already has and we don't want to spam the user.
    return element.asType().getKind() != TypeKind.ERROR;
  }

  private boolean installInCheckDisabled(Element element) {
    return DISABLE_MODULES_HAVE_INSTALL_IN_CHECK.get(getProcessingEnv())
        || Processors.hasAnnotation(element, ClassNames.DISABLE_INSTALL_IN_CHECK);
  }

  /**
   * When using Dagger Producers, don't process generated modules. They will not have the expected
   * annotations.
   */
  private static boolean isDaggerGeneratedModule(Element element) {
    if (!Processors.hasAnnotation(element, ClassNames.MODULE)) {
      return false;
    }
    return element.getAnnotationMirrors().stream()
        .filter(mirror -> isGenerated(mirror))
        .map(mirror -> asString(getOnlyElement(asList(getAnnotationValue(mirror, "value")))))
        .anyMatch(value -> value.startsWith("dagger"));
  }

  private static List<? extends AnnotationValue> asList(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor8<List<? extends AnnotationValue>, Void>() {
          @Override
          public List<? extends AnnotationValue> visitArray(
              List<? extends AnnotationValue> value, Void unused) {
            return value;
          }
        },
        null);
  }

  private static String asString(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String value, Void unused) {
            return value;
          }
        },
        null);
  }

  private static boolean isGenerated(AnnotationMirror annotationMirror) {
    Name name = asType(annotationMirror.getAnnotationType().asElement()).getQualifiedName();
    return name.contentEquals("javax.annotation.Generated")
        || name.contentEquals("javax.annotation.processing.Generated");
  }
}
