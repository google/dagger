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

import static com.google.auto.common.MoreElements.asType;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dummy files to propagate information through multiple javac runs. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class AggregatedDepsProcessor extends BaseProcessor {
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

    if (isModule && hasInstallIn) {
      ProcessorErrors.checkState(
          element.getKind() == CLASS || element.getKind() == INTERFACE,
          element,
          "Only classes and interfaces can be annotated with @Module: %s",
          element);
      TypeElement module = asType(element);

      // TODO(b/28989613): This should really be fixed in Dagger. Remove once Dagger bug is fixed.
      List<ExecutableElement> abstractMethodsWithMissingBinds =
          ElementFilter.methodsIn(module.getEnclosedElements()).stream()
              .filter(method -> method.getModifiers().contains(ABSTRACT))
              .filter(method -> !Processors.hasDaggerAbstractMethodAnnotation(method))
              .collect(toList());
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
}
