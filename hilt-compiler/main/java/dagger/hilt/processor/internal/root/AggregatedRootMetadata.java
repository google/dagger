/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import static androidx.room3.compiler.processing.compat.XConverters.getProcessingEnv;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.AggregatedRootIr;
import java.util.List;

/**
 * Represents the values stored in an {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot}.
 */
@AutoValue
abstract class AggregatedRootMetadata {

  /** Returns the name of the aggregating element. */
  public abstract ClassName name();

  /** Returns the element that was annotated with the root annotation. */
  abstract XTypeElement rootElement();

  /**
   * Returns the originating root element. In most cases this will be the same as {@link
   * #rootElement()}.
   */
  abstract ClassName originatingRootName();

  /** Returns the root annotation class name. */
  abstract ClassName rootAnnotationName();

  /** Returns the name of the root component for this root. */
  abstract ClassName rootComponentName();

  /** Returns whether this root can use a shared component. */
  abstract boolean allowsSharingComponent();

  @Memoized
  RootType rootType() {
    return RootType.of(rootElement());
  }

  public static AggregatedRootIr toIr(AggregatedRootMetadata metadata) {
    return new AggregatedRootIr(
        metadata.name(),
        metadata.rootElement().getClassName(),
        metadata.originatingRootName(),
        metadata.rootAnnotationName(),
        metadata.rootComponentName(),
        metadata.allowsSharingComponent());
  }

  static ImmutableSet<AggregatedRootMetadata> from(XProcessingEnv env) {
    return AggregatedElements.from(
            ClassNames.AGGREGATED_ROOT_PACKAGE, ClassNames.AGGREGATED_ROOT, env)
        .stream()
        .map(AggregatedRootMetadata::from)
        .collect(toImmutableSet());
  }

  /** Returns metadata for the given aggregated element. */
  public static AggregatedRootMetadata from(XTypeElement element) {
    XProcessingEnv env = getProcessingEnv(element);
    XAnnotation annotation = element.getAnnotation(ClassNames.AGGREGATED_ROOT);

    XTypeElement rootElement = env.requireTypeElement(annotation.getAsString("root"));
    boolean allowSharingComponent = true;
    return new AutoValue_AggregatedRootMetadata(
        element.getClassName(),
        rootElement,
        env.requireTypeElement(annotation.getAsString("originatingRoot")).getClassName(),
        annotation.getAsType("rootAnnotation").getTypeElement().getClassName(),
        parseRootComponentClassName(
            annotation.getAsString("rootComponentPackage"),
            annotation.getAsStringList("rootComponentSimpleNames")),
        allowSharingComponent);
  }

  private static ClassName parseRootComponentClassName(
      String rootComponentPackage, List<String> rootComponentSimpleNames) {
    // If rootComponentPackage isn't there, that means that this is likely coming from an older
    // Dagger version, so assume the root component is the SingletonComponent for backwards
    // compatibility.
    if (rootComponentPackage.isEmpty()) {
      return ClassNames.SINGLETON_COMPONENT;
    }
    return ClassName.get(
        rootComponentPackage,
        rootComponentSimpleNames.get(0),
        rootComponentSimpleNames.subList(1, rootComponentSimpleNames.size())
            .toArray(new String[0]));
  }
}
