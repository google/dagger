/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.uninstallmodules;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;

/** Validates {@link dagger.hilt.android.testing.UninstallModules} usages. */
public final class UninstallModulesProcessingStep extends BaseProcessingStep {

  public UninstallModulesProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.UNINSTALL_MODULES);
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    // TODO(bcorso): Consider using RootType to check this?
    // TODO(bcorso): Loosen this restriction to allow defining sets of ignored modules in libraries.
    ProcessorErrors.checkState(
        isTypeElement(element) && element.hasAnnotation(ClassNames.HILT_ANDROID_TEST),
        element,
        "@%s should only be used on test classes annotated with @%s, but found: %s",
        annotation.simpleName(),
        ClassNames.HILT_ANDROID_TEST.simpleName(),
        XElements.toStableString(element));

    XTypeElement testElement = XElements.asTypeElement(element);
    ImmutableList<XTypeElement> uninstallModules =
        XAnnotations.getAsTypeElementList(
            testElement.getAnnotation(ClassNames.UNINSTALL_MODULES), "value");

    checkModulesHaveInstallIn(testElement, uninstallModules);
    checkModulesDontOriginateFromTest(testElement, uninstallModules);

    new AggregatedUninstallModulesGenerator(testElement, uninstallModules).generate();
  }

  private void checkModulesHaveInstallIn(
      XTypeElement testElement, ImmutableList<XTypeElement> uninstallModules) {
    ImmutableList<XTypeElement> invalidModules =
        uninstallModules.stream()
            .filter(
                module ->
                    !(module.hasAnnotation(ClassNames.MODULE)
                        && module.hasAnnotation(ClassNames.INSTALL_IN)))
            .collect(toImmutableList());

    ProcessorErrors.checkState(
        invalidModules.isEmpty(),
        // TODO(b/152801981): Point to the annotation value rather than the annotated element.
        testElement,
        "@UninstallModules should only include modules annotated with both @Module and @InstallIn, "
            + "but found: %s.",
        invalidModules.stream().map(XElements::toStableString).collect(toImmutableList()));
  }

  private void checkModulesDontOriginateFromTest(
      XTypeElement testElement, ImmutableList<XTypeElement> uninstallModules) {
    ImmutableList<ClassName> invalidModules =
        uninstallModules.stream()
            .filter(module -> Processors.getOriginatingTestElement(module).isPresent())
            .map(XTypeElement::getClassName)
            .collect(toImmutableList());

    ProcessorErrors.checkState(
        invalidModules.isEmpty(),
        // TODO(b/152801981): Point to the annotation value rather than the annotated element.
        testElement,
        "@UninstallModules should not contain test modules, but found: %s",
        invalidModules);
  }
}
