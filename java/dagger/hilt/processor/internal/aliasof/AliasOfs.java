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

package dagger.hilt.processor.internal.aliasof;

import static com.google.common.base.Suppliers.memoize;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Extracts a multimap of aliases annotated with {@link dagger.hilt.migration.AliasOf} mapping them
 * to scopes they are alias of.
 */
public final class AliasOfs {
  static final String AGGREGATING_PACKAGE = AliasOfs.class.getPackage().getName() + ".codegen";

  private final ProcessingEnvironment processingEnvironment;
  private final ImmutableSet<ClassName> defineComponentScopes;
  private final Supplier<ImmutableMultimap<ClassName, ClassName>> aliases =
      memoize(() -> getAliases());

  public AliasOfs(
      ProcessingEnvironment processingEnvironment, ImmutableSet<ClassName> defineComponentScopes) {
    this.defineComponentScopes = defineComponentScopes;
    this.processingEnvironment = processingEnvironment;
  }

  public ImmutableSet<ClassName> getAliasesFor(ClassName defineComponentScope) {
    return ImmutableSet.copyOf(aliases.get().get(defineComponentScope));
  }

  private ImmutableMultimap<ClassName, ClassName> getAliases() {
    Elements elements = processingEnvironment.getElementUtils();
    PackageElement packageElement = elements.getPackageElement(AGGREGATING_PACKAGE);
    if (packageElement == null) {
      return ImmutableMultimap.of();
    }
    List<? extends Element> scopeAliasElements = packageElement.getEnclosedElements();
    Preconditions.checkState(
        !scopeAliasElements.isEmpty(), "No scope aliases Found in package %s.", packageElement);

    ImmutableMultimap.Builder<ClassName, ClassName> builder = ImmutableMultimap.builder();
    for (Element element : scopeAliasElements) {
      ProcessorErrors.checkState(
          element.getKind() == ElementKind.CLASS,
          element,
          "Only classes may be in package %s. Did you add custom code in the package?",
          packageElement);

      AnnotationMirror annotationMirror =
          Processors.getAnnotationMirror(element, ClassNames.ALIAS_OF_PROPAGATED_DATA);

      ProcessorErrors.checkState(
          annotationMirror != null,
          element,
          "Classes in package %s must be annotated with @%s: %s."
              + " Found: %s. Files in this package are generated, did you add custom code in the"
              + " package? ",
          packageElement,
          ClassNames.ALIAS_OF_PROPAGATED_DATA,
          element.getSimpleName(),
          element.getAnnotationMirrors());

      TypeElement defineComponentScope =
          Processors.getAnnotationClassValue(elements, annotationMirror, "defineComponentScope");
      TypeElement alias = Processors.getAnnotationClassValue(elements, annotationMirror, "alias");

      Preconditions.checkState(
          defineComponentScopes.contains(ClassName.get(defineComponentScope)),
          "The scope %s cannot be an alias for %s. You can only have aliases of a scope defined"
              + " directly on a @DefineComponent type.",
          ClassName.get(alias),
          ClassName.get(defineComponentScope));

      builder.put(ClassName.get(defineComponentScope), ClassName.get(alias));
    }

    return builder.build();
  }
}
