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
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isModuleInstallInCheckDisabled;
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
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
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

  private static final ImmutableSet<ClassName> ENTRY_POINT_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ENTRY_POINT,
          ClassNames.EARLY_ENTRY_POINT,
          ClassNames.GENERATED_ENTRY_POINT,
          ClassNames.COMPONENT_ENTRY_POINT);

  private static final ImmutableSet<ClassName> MODULE_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.MODULE);

  private static final ImmutableSet<ClassName> INSTALL_IN_ANNOTATIONS =
      ImmutableSet.of(ClassNames.INSTALL_IN, ClassNames.TEST_INSTALL_IN);

  private final Set<Element> seen = new HashSet<>();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.builder()
        .addAll(INSTALL_IN_ANNOTATIONS)
        .addAll(MODULE_ANNOTATIONS)
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

    Optional<ClassName> installInAnnotation = getAnnotation(element, INSTALL_IN_ANNOTATIONS);
    Optional<ClassName> entryPointAnnotation = getAnnotation(element, ENTRY_POINT_ANNOTATIONS);
    Optional<ClassName> moduleAnnotation = getAnnotation(element, MODULE_ANNOTATIONS);

    boolean hasInstallIn = installInAnnotation.isPresent();
    boolean isEntryPoint = entryPointAnnotation.isPresent();
    boolean isModule = moduleAnnotation.isPresent();

    ProcessorErrors.checkState(
        !hasInstallIn || isEntryPoint || isModule,
        element,
        "@%s-annotated classes must also be annotated with @Module or @EntryPoint: %s",
        installInAnnotation.map(ClassName::simpleName).orElse("@InstallIn"),
        element);

    ProcessorErrors.checkState(
        !(isEntryPoint && isModule),
        element,
        "@%s and @%s cannot be used on the same interface: %s",
        moduleAnnotation.map(ClassName::simpleName).orElse("@Module"),
        entryPointAnnotation.map(ClassName::simpleName).orElse("@EntryPoint"),
        element);

    if (isModule) {
      processModule(element, installInAnnotation, moduleAnnotation.get());
    } else if (isEntryPoint) {
      processEntryPoint(element, installInAnnotation, entryPointAnnotation.get());
    } else {
      throw new AssertionError();
    }
  }

  private void processModule(
      Element element, Optional<ClassName> installInAnnotation, ClassName moduleAnnotation)
      throws Exception {
    ProcessorErrors.checkState(
        installInAnnotation.isPresent()
            || isDaggerGeneratedModule(element)
            || installInCheckDisabled(element),
        element,
        "%s is missing an @InstallIn annotation. If this was intentional, see"
            + " https://dagger.dev/hilt/flags#disable-install-in-check for how to disable this"
            + " check.",
        element);

    if (!installInAnnotation.isPresent()) {
      // Modules without @InstallIn or @TestInstallIn annotations don't need to be processed further
      return;
    }

    ProcessorErrors.checkState(
        element.getKind() == CLASS || element.getKind() == INTERFACE,
        element,
        "Only classes and interfaces can be annotated with @Module: %s",
        element);
    TypeElement module = asType(element);

    ProcessorErrors.checkState(
        Processors.isTopLevel(module)
            || module.getModifiers().contains(STATIC)
            || module.getModifiers().contains(ABSTRACT)
            || Processors.hasAnnotation(module.getEnclosingElement(), ClassNames.HILT_ANDROID_TEST),
        module,
        "Nested @%s modules must be static unless they are directly nested within a test. "
            + "Found: %s",
        installInAnnotation.get().simpleName(),
        module);

    // Check that if Dagger needs an instance of the module, Hilt can provide it automatically by
    // calling a visible empty constructor.
    ProcessorErrors.checkState(
        // Skip ApplicationContextModule, since Hilt manages this module internally.
        ClassNames.APPLICATION_CONTEXT_MODULE.equals(ClassName.get(module))
            || !Processors.requiresModuleInstance(getElementUtils(), module)
            || Processors.hasVisibleEmptyConstructor(module),
        module,
        "Modules that need to be instantiated by Hilt must have a visible, empty constructor.");

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

    ImmutableList<TypeElement> replacedModules = ImmutableList.of();
    if (Processors.hasAnnotation(module, ClassNames.TEST_INSTALL_IN)) {
      Optional<TypeElement> originatingTestElement =
          Processors.getOriginatingTestElement(module, getElementUtils());
      ProcessorErrors.checkState(
          !originatingTestElement.isPresent(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn modules cannot be nested in (or originate from) a "
              + "@HiltAndroidTest-annotated class:  %s",
          originatingTestElement
              .map(testElement -> testElement.getQualifiedName().toString())
              .orElse(""));

      AnnotationMirror testInstallIn =
          Processors.getAnnotationMirror(module, ClassNames.TEST_INSTALL_IN);
      replacedModules =
          Processors.getAnnotationClassValues(getElementUtils(), testInstallIn, "replaces");

      ProcessorErrors.checkState(
          !replacedModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot be empty. Use @InstallIn instead.");

      ImmutableList<TypeElement> nonInstallInModules =
          replacedModules.stream()
              .filter(
                  replacedModule ->
                      !Processors.hasAnnotation(replacedModule, ClassNames.INSTALL_IN))
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          nonInstallInModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() can only contain @InstallIn modules, but found: %s",
          nonInstallInModules);

      ImmutableList<TypeElement> hiltWrapperModules =
          replacedModules.stream()
              .filter(
                  replacedModule ->
                      replacedModule.getSimpleName().toString().startsWith("HiltWrapper_"))
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          hiltWrapperModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot contain Hilt generated public wrapper modules, "
              + "but found: %s. ",
          hiltWrapperModules);

      if (!getPackage(module).getQualifiedName().toString().startsWith("dagger.hilt")) {
        // Prevent external users from overriding Hilt's internal modules. Techincally, except for
        // ApplicationContextModule, making all modules pkg-private should be enough but this is an
        // extra measure of precaution.
        ImmutableList<TypeElement> hiltInternalModules =
            replacedModules.stream()
                .filter(
                    replacedModule ->
                        getPackage(replacedModule)
                            .getQualifiedName()
                            .toString()
                            .startsWith("dagger.hilt"))
                .collect(toImmutableList());

        ProcessorErrors.checkState(
            hiltInternalModules.isEmpty(),
            // TODO(b/152801981): this should really error on the annotation value
            module,
            "@TestInstallIn#replaces() cannot contain internal Hilt modules, but found: %s. ",
            hiltInternalModules);
      }

      // Prevent users from uninstalling test-specific @InstallIn modules.
      ImmutableList<TypeElement> replacedTestSpecificInstallIn =
          replacedModules.stream()
              .filter(
                  replacedModule ->
                      Processors.getOriginatingTestElement(replacedModule, getElementUtils())
                          .isPresent())
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          replacedTestSpecificInstallIn.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot replace test specific @InstallIn modules, but found: "
              + "%s. Please remove the @InstallIn module manually rather than replacing it.",
          replacedTestSpecificInstallIn);
    }

    generateAggregatedDeps(
        "modules",
        module,
        moduleAnnotation,
        replacedModules.stream().map(ClassName::get).collect(toImmutableSet()));
  }

  private void processEntryPoint(
      Element element, Optional<ClassName> installInAnnotation, ClassName entryPointAnnotation)
      throws Exception {
    ProcessorErrors.checkState(
        installInAnnotation.isPresent() ,
        element,
        "@%s %s must also be annotated with @InstallIn",
        entryPointAnnotation.simpleName(),
        element);

    ProcessorErrors.checkState(
        !Processors.hasAnnotation(element, ClassNames.TEST_INSTALL_IN),
        element,
        "@TestInstallIn can only be used with modules");

    ProcessorErrors.checkState(
        element.getKind() == INTERFACE,
        element,
        "Only interfaces can be annotated with @%s: %s",
        entryPointAnnotation.simpleName(),
        element);
    TypeElement entryPoint = asType(element);

    if (entryPointAnnotation.equals(ClassNames.EARLY_ENTRY_POINT)) {
      ImmutableSet<ClassName> components = Components.getComponents(getElementUtils(), element);
      ProcessorErrors.checkState(
          components.equals(ImmutableSet.of(ClassNames.SINGLETON_COMPONENT)),
          element,
          "@EarlyEntryPoint can only be installed into the SingletonComponent. Found: %s",
          components);

      Optional<TypeElement> optionalTestElement =
          Processors.getOriginatingTestElement(element, getElementUtils());
      ProcessorErrors.checkState(
          !optionalTestElement.isPresent(),
          element,
          "@EarlyEntryPoint-annotated entry point, %s, cannot be nested in (or originate from) "
              + "a @HiltAndroidTest-annotated class, %s. This requirement is to avoid confusion "
              + "with other, test-specific entry points.",
          asType(element).getQualifiedName().toString(),
          optionalTestElement
              .map(testElement -> testElement.getQualifiedName().toString())
              .orElse(""));
    }

    generateAggregatedDeps(
        entryPointAnnotation.equals(ClassNames.COMPONENT_ENTRY_POINT)
            ? "componentEntryPoints"
            : "entryPoints",
        entryPoint,
        entryPointAnnotation,
        ImmutableSet.of());
  }

  private void generateAggregatedDeps(
      String key,
      TypeElement element,
      ClassName annotation,
      ImmutableSet<ClassName> replacedModules)
      throws Exception {
    // Get @InstallIn components here to catch errors before skipping user's pkg-private element.
    ImmutableSet<ClassName> components = Components.getComponents(getElementUtils(), element);

    if (isValidKind(element)) {
      Optional<PkgPrivateMetadata> pkgPrivateMetadata =
          PkgPrivateMetadata.of(getElementUtils(), element, annotation);
      if (pkgPrivateMetadata.isPresent()) {
        if (key.contentEquals("modules")) {
          new PkgPrivateModuleGenerator(getProcessingEnv(), pkgPrivateMetadata.get()).generate();
        } else {
          new PkgPrivateEntryPointGenerator(getProcessingEnv(), pkgPrivateMetadata.get())
              .generate();
        }
      } else {
        Optional<ClassName> testName =
            Processors.getOriginatingTestElement(element, getElementUtils()).map(ClassName::get);
        new AggregatedDepsGenerator(
                key, element, testName, components, replacedModules, getProcessingEnv())
            .generate();
      }
    }
  }

  private static Optional<ClassName> getAnnotation(
      Element element, ImmutableSet<ClassName> annotations) {
    ImmutableSet<ClassName> usedAnnotations =
        annotations.stream()
            .filter(annotation -> Processors.hasAnnotation(element, annotation))
            .collect(toImmutableSet());

    if (usedAnnotations.isEmpty()) {
      return Optional.empty();
    }

    ProcessorErrors.checkState(
        usedAnnotations.size() == 1,
        element,
        "Only one of the following annotations can be used on %s: %s",
        element,
        usedAnnotations);

    return Optional.of(getOnlyElement(usedAnnotations));
  }

  private static boolean isValidKind(Element element) {
    // don't go down the rabbit hole of analyzing undefined types. N.B. we don't issue
    // an error here because javac already has and we don't want to spam the user.
    return element.asType().getKind() != TypeKind.ERROR;
  }

  private boolean installInCheckDisabled(Element element) {
    return isModuleInstallInCheckDisabled(getProcessingEnv())
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
