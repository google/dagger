/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.internal.codegen.processingstep;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.writing.LazyMapKeyProxyGenerator;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Generate keep rules for LazyClassKey referenced classes to prevent class merging. */
final class LazyClassKeyProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private static final String PROGUARD_KEEP_RULE = "-keep,allowobfuscation,allowshrinking class ";

  // Note: We aggregate @LazyClassKey usages across processing rounds, so we use ClassName instead
  // of XElement as the map key to avoid storing XElement instances across processing rounds.
  private final SetMultimap<XClassName, XClassName> lazyMapKeysByModule =
      LinkedHashMultimap.create();
  private final LazyMapKeyProxyGenerator lazyMapKeyProxyGenerator;

  @Inject
  LazyClassKeyProcessingStep(LazyMapKeyProxyGenerator lazyMapKeyProxyGenerator) {
    this.lazyMapKeyProxyGenerator = lazyMapKeyProxyGenerator;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.LAZY_CLASS_KEY);
  }

  @Override
  protected void process(XElement element, ImmutableSet<XClassName> annotations) {
    XClassName lazyClassKey =
        element
            .getAnnotation(XTypeNames.LAZY_CLASS_KEY)
            .getAsType("value")
            .getTypeElement()
            .asClassName();
    // No need to fail, since we want to support customized usage of class key annotations.
    // https://github.com/google/dagger/pull/2831
    if (!isMapBinding(element) || !isModuleOrProducerModule(element.getEnclosingElement())) {
      return;
    }
    XTypeElement moduleElement = XElements.asTypeElement(element.getEnclosingElement());
    lazyMapKeysByModule.put(moduleElement.asClassName(), lazyClassKey);
    XMethodElement method = XElements.asMethod(element);
    lazyMapKeyProxyGenerator.generate(method);
  }

  private static boolean isMapBinding(XElement element) {
    return element.hasAnnotation(XTypeNames.INTO_MAP)
        && (element.hasAnnotation(XTypeNames.BINDS)
            || element.hasAnnotation(XTypeNames.PROVIDES)
            || element.hasAnnotation(XTypeNames.PRODUCES));
  }

  private static boolean isModuleOrProducerModule(XElement element) {
    return isTypeElement(element)
        && (element.hasAnnotation(XTypeNames.MODULE)
            || element.hasAnnotation(XTypeNames.PRODUCER_MODULE));
  }

  // TODO(b/386393062): Avoid generating proguard files in processOver.
  @Override
  public void processOver(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    super.processOver(env, elementsByAnnotation);
    lazyMapKeysByModule
        .asMap()
        .forEach(
            (moduleClassName, lazyClassKeys) -> {
              // Note: we could probably get better incremental performance by using the method
              // element instead of the module element as the originating element. However, that
              // would require appending the method name to each proguard file, which would probably
              // cause issues with the filename length limit (256 characters) given it already must
              // include the module's fully qualified name.
              XTypeElement originatingElement = env.requireTypeElement(moduleClassName);

              Path proguardFile =
                  Path.of(
                      "META-INF/proguard",
                      getFullyQualifiedEnclosedClassName(moduleClassName) + "_LazyClassKeys.pro");

              String proguardFileContents =
                  lazyClassKeys.stream()
                      .map(lazyClassKey -> PROGUARD_KEEP_RULE + lazyClassKey.getCanonicalName())
                      .collect(joining("\n"));

              writeResource(env.getFiler(), originatingElement, proguardFile, proguardFileContents);
            });
    // Processing is over so this shouldn't matter, but clear the map just incase.
    lazyMapKeysByModule.clear();
  }

  private void writeResource(
      XFiler filer, XElement originatingElement, Path path, String contents) {
    try (OutputStream outputStream =
            filer.writeResource(path, ImmutableList.of(originatingElement), XFiler.Mode.Isolating);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      writer.write(contents);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Returns the fully qualified class name, with _ instead of . */
  private static String getFullyQualifiedEnclosedClassName(XClassName className) {
    return Joiner.on('_')
        .join(
            ImmutableList.<String>builder()
                .add(className.getPackageName().replace('.', '_'))
                .addAll(className.getSimpleNames())
                .build());
  }
}
