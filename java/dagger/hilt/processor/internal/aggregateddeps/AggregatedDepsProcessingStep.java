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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isModuleInstallInCheckDisabled;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Components;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Processor that outputs dummy files to propagate information through multiple javac runs. */
public final class AggregatedDepsProcessingStep extends BaseProcessingStep {

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

  private final Set<XElement> seen = new HashSet<>();

  public AggregatedDepsProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.<ClassName>builder()
        .addAll(INSTALL_IN_ANNOTATIONS)
        .addAll(MODULE_ANNOTATIONS)
        .addAll(ENTRY_POINT_ANNOTATIONS)
        .build();
  }

  @Override
  public void processEach(ClassName annotation, XElement element) throws Exception {
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
        XElements.toStableString(element));

    ProcessorErrors.checkState(
        !(isEntryPoint && isModule),
        element,
        "@%s and @%s cannot be used on the same interface: %s",
        moduleAnnotation.map(ClassName::simpleName).orElse("@Module"),
        entryPointAnnotation.map(ClassName::simpleName).orElse("@EntryPoint"),
        XElements.toStableString(element));

    if (isModule) {
      processModule(element, installInAnnotation, moduleAnnotation.get());
    } else if (isEntryPoint) {
      processEntryPoint(element, installInAnnotation, entryPointAnnotation.get());
    } else {
      throw new AssertionError();
    }
  }

  private void processModule(
      XElement element, Optional<ClassName> installInAnnotation, ClassName moduleAnnotation)
      throws Exception {
    ProcessorErrors.checkState(
        installInAnnotation.isPresent()
            || isDaggerGeneratedModule(element)
            || installInCheckDisabled(element),
        element,
        "%s is missing an @InstallIn annotation. If this was intentional, see"
            + " https://dagger.dev/hilt/flags#disable-install-in-check for how to disable this"
            + " check.",
        XElements.toStableString(element));

    if (!installInAnnotation.isPresent()) {
      // Modules without @InstallIn or @TestInstallIn annotations don't need to be processed further
      return;
    }

    ProcessorErrors.checkState(
        XElementKt.isTypeElement(element),
        element,
        "Only classes and interfaces can be annotated with @Module: %s",
        XElements.toStableString(element));

    XTypeElement module = XElements.asTypeElement(element);

    ProcessorErrors.checkState(
        module.isClass() || module.isInterface() || module.isKotlinObject(),
        module,
        "Only classes and interfaces can be annotated with @Module: %s",
        XElements.toStableString(module));

    ProcessorErrors.checkState(
        Processors.isTopLevel(module)
            || module.isStatic()
            || module.isAbstract()
            || module.getEnclosingElement().hasAnnotation(ClassNames.HILT_ANDROID_TEST),
        module,
        "Nested @%s modules must be static unless they are directly nested within a test. "
            + "Found: %s",
        installInAnnotation.get().simpleName(),
        XElements.toStableString(module));

    // Check that if Dagger needs an instance of the module, Hilt can provide it automatically by
    // calling a visible empty constructor.
    ProcessorErrors.checkState(
        // Skip ApplicationContextModule, since Hilt manages this module internally.
        ClassNames.APPLICATION_CONTEXT_MODULE.equals(module.getClassName())
            || !Processors.requiresModuleInstance(module)
            || Processors.hasVisibleEmptyConstructor(module),
        module,
        "Modules that need to be instantiated by Hilt must have a visible, empty constructor.");

    // TODO(b/28989613): This should really be fixed in Dagger. Remove once Dagger bug is fixed.
    ImmutableList<XExecutableElement> abstractMethodsWithMissingBinds =
        module.getDeclaredMethods().stream()
            .filter(XMethodElement::isAbstract)
            .filter(method -> !Processors.hasDaggerAbstractMethodAnnotation(method))
            .collect(toImmutableList());
    ProcessorErrors.checkState(
        abstractMethodsWithMissingBinds.isEmpty(),
        module,
        "Found unimplemented abstract methods, %s, in an abstract module, %s. "
            + "Did you forget to add a Dagger binding annotation (e.g. @Binds)?",
        abstractMethodsWithMissingBinds.stream()
            .map(XElements::toStableString)
            .collect(DaggerStreams.toImmutableList()),
        XElements.toStableString(module));

    ImmutableList<XTypeElement> replacedModules = ImmutableList.of();
    if (module.hasAnnotation(ClassNames.TEST_INSTALL_IN)) {
      Optional<XTypeElement> originatingTestElement = Processors.getOriginatingTestElement(module);
      ProcessorErrors.checkState(
          !originatingTestElement.isPresent(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn modules cannot be nested in (or originate from) a "
              + "@HiltAndroidTest-annotated class:  %s",
          originatingTestElement.map(XTypeElement::getQualifiedName).orElse(""));

      XAnnotation testInstallIn = module.getAnnotation(ClassNames.TEST_INSTALL_IN);
      replacedModules = Processors.getAnnotationClassValues(testInstallIn, "replaces");

      ProcessorErrors.checkState(
          !replacedModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot be empty. Use @InstallIn instead.");

      ImmutableList<XTypeElement> nonInstallInModules =
          replacedModules.stream()
              .filter(replacedModule -> !replacedModule.hasAnnotation(ClassNames.INSTALL_IN))
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          nonInstallInModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() can only contain @InstallIn modules, but found: %s",
          nonInstallInModules.stream()
              .map(XElements::toStableString)
              .collect(DaggerStreams.toImmutableList()));

      ImmutableList<XTypeElement> hiltWrapperModules =
          replacedModules.stream()
              .filter(
                  replacedModule ->
                      replacedModule.getClassName().simpleName().startsWith("HiltWrapper_"))
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          hiltWrapperModules.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot contain Hilt generated public wrapper modules, "
              + "but found: %s. ",
          hiltWrapperModules.stream()
              .map(XElements::toStableString)
              .collect(DaggerStreams.toImmutableList()));

      if (!module.getPackageName().startsWith("dagger.hilt")) {
        // Prevent external users from overriding Hilt's internal modules. Techincally, except for
        // ApplicationContextModule, making all modules pkg-private should be enough but this is an
        // extra measure of precaution.
        ImmutableList<XTypeElement> hiltInternalModules =
            replacedModules.stream()
                .filter(replacedModule -> replacedModule.getPackageName().startsWith("dagger.hilt"))
                .collect(toImmutableList());

        ProcessorErrors.checkState(
            hiltInternalModules.isEmpty(),
            // TODO(b/152801981): this should really error on the annotation value
            module,
            "@TestInstallIn#replaces() cannot contain internal Hilt modules, but found: %s. ",
            hiltInternalModules.stream()
                .map(XElements::toStableString)
                .collect(DaggerStreams.toImmutableList()));
      }

      // Prevent users from uninstalling test-specific @InstallIn modules.
      ImmutableList<XTypeElement> replacedTestSpecificInstallIn =
          replacedModules.stream()
              .filter(
                  replacedModule ->
                      Processors.getOriginatingTestElement(replacedModule).isPresent())
              .collect(toImmutableList());

      ProcessorErrors.checkState(
          replacedTestSpecificInstallIn.isEmpty(),
          // TODO(b/152801981): this should really error on the annotation value
          module,
          "@TestInstallIn#replaces() cannot replace test specific @InstallIn modules, but found: "
              + "%s. Please remove the @InstallIn module manually rather than replacing it.",
          replacedTestSpecificInstallIn.stream()
              .map(XElements::toStableString)
              .collect(DaggerStreams.toImmutableList()));
    }

    generateAggregatedDeps(
        "modules",
        module,
        moduleAnnotation,
        replacedModules.stream().map(XTypeElement::getClassName).collect(toImmutableSet()));
  }

  private void processEntryPoint(
      XElement element, Optional<ClassName> installInAnnotation, ClassName entryPointAnnotation)
      throws Exception {
    ProcessorErrors.checkState(
        installInAnnotation.isPresent() ,
        element,
        "@%s %s must also be annotated with @InstallIn",
        entryPointAnnotation.simpleName(),
        XElements.toStableString(element));

    ProcessorErrors.checkState(
        !element.hasAnnotation(ClassNames.TEST_INSTALL_IN),
        element,
        "@TestInstallIn can only be used with modules");

    ProcessorErrors.checkState(
        XElementKt.isTypeElement(element) && XElements.asTypeElement(element).isInterface(),
        element,
        "Only interfaces can be annotated with @%s: %s",
        entryPointAnnotation.simpleName(),
        XElements.toStableString(element));
    XTypeElement entryPoint = XElements.asTypeElement(element);

    if (entryPointAnnotation.equals(ClassNames.EARLY_ENTRY_POINT)) {
      ImmutableSet<ClassName> components = Components.getComponents(element);
      ProcessorErrors.checkState(
          components.equals(ImmutableSet.of(ClassNames.SINGLETON_COMPONENT)),
          element,
          "@EarlyEntryPoint can only be installed into the SingletonComponent. Found: %s",
          components);

      Optional<XTypeElement> optionalTestElement = Processors.getOriginatingTestElement(element);
      ProcessorErrors.checkState(
          !optionalTestElement.isPresent(),
          element,
          "@EarlyEntryPoint-annotated entry point, %s, cannot be nested in (or originate from) "
              + "a @HiltAndroidTest-annotated class, %s. This requirement is to avoid confusion "
              + "with other, test-specific entry points.",
          entryPoint.getQualifiedName(),
          optionalTestElement.map(testElement -> testElement.getQualifiedName()).orElse(""));
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
      XTypeElement element,
      ClassName annotation,
      ImmutableSet<ClassName> replacedModules)
      throws Exception {
    // Get @InstallIn components here to catch errors before skipping user's pkg-private element.
    ImmutableSet<ClassName> components = Components.getComponents(element);

    if (isValidKind(element)) {
      Optional<PkgPrivateMetadata> pkgPrivateMetadata = PkgPrivateMetadata.of(element, annotation);
      if (pkgPrivateMetadata.isPresent()) {
        if (key.contentEquals("modules")) {
          new PkgPrivateModuleGenerator(processingEnv(), pkgPrivateMetadata.get()).generate();
        } else {
          new PkgPrivateEntryPointGenerator(processingEnv(), pkgPrivateMetadata.get()).generate();
        }
      } else {
        Optional<ClassName> testName =
            Processors.getOriginatingTestElement(element).map(XTypeElement::getClassName);
        new AggregatedDepsGenerator(key, element, testName, components, replacedModules).generate();
      }
    }
  }

  private static Optional<ClassName> getAnnotation(
      XElement element, ImmutableSet<ClassName> annotations) {
    ImmutableSet<ClassName> usedAnnotations =
        annotations.stream().filter(element::hasAnnotation).collect(toImmutableSet());

    if (usedAnnotations.isEmpty()) {
      return Optional.empty();
    }

    ProcessorErrors.checkState(
        usedAnnotations.size() == 1,
        element,
        "Only one of the following annotations can be used on %s: %s",
        XElements.toStableString(element),
        usedAnnotations);

    return Optional.of(getOnlyElement(usedAnnotations));
  }

  private static boolean isValidKind(XElement element) {
    // don't go down the rabbit hole of analyzing undefined types. N.B. we don't issue
    // an error here because javac already has and we don't want to spam the user.
    return !XElements.asTypeElement(element).getType().isError();
  }

  private boolean installInCheckDisabled(XElement element) {
    return isModuleInstallInCheckDisabled(processingEnv())
        || element.hasAnnotation(ClassNames.DISABLE_INSTALL_IN_CHECK);
  }

  /**
   * When using Dagger Producers, don't process generated modules. They will not have the expected
   * annotations.
   */
  private static boolean isDaggerGeneratedModule(XElement element) {
    if (!element.hasAnnotation(ClassNames.MODULE)) {
      return false;
    }
    return element.getAllAnnotations().stream()
        .filter(annotation -> isGenerated(annotation))
        .map(annotation -> getOnlyElement(annotation.getAsStringList("value")))
        .anyMatch(value -> value.startsWith("dagger"));
  }

  private static boolean isGenerated(XAnnotation annotation) {
    String name = annotation.getTypeElement().getQualifiedName();

    return name.equals("javax.annotation.Generated")
        || name.equals("javax.annotation.processing.Generated");
  }
}
