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

package dagger.internal.codegen.base;

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.CAST;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.DEPRECATION;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.KOTLIN_INTERNAL;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.UNINITIALIZED;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;

import androidx.room.compiler.codegen.CodeLanguage;
import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XFileSpec;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XAnnotationSpecs;
import dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import java.util.Optional;

/**
 * A template class that provides a framework for properly handling IO while generating source files
 * from an annotation processor. Particularly, it makes a best effort to ensure that files that fail
 * to write successfully are deleted.
 *
 * @param <T> The input type from which source is to be generated.
 */
public abstract class SourceFileGenerator<T> {
  private static final String GENERATED_COMMENTS = "https://dagger.dev";

  private final XFiler filer;
  private final XProcessingEnv processingEnv;

  public SourceFileGenerator(XFiler filer, XProcessingEnv processingEnv) {
    this.filer = checkNotNull(filer);
    this.processingEnv = checkNotNull(processingEnv);
  }

  public SourceFileGenerator(SourceFileGenerator<T> delegate) {
    this(delegate.filer, delegate.processingEnv);
  }

  /** Generates a source file to be compiled for {@code T}. */
  public void generate(T input, XMessager messager) {
    generate(input);
  }

  /** Generates a source file to be compiled for {@code T}. */
  public void generate(T input) {
    for (XTypeSpec type : topLevelTypes(input)) {
       buildFile(input, XTypeSpecs.toBuilder(type))
           .writeTo(codeLanguage(), filer, XFiler.Mode.Isolating);
    }
  }

  public CodeLanguage codeLanguage() {
    return CodeLanguage.JAVA;
  }

  private XFileSpec buildFile(T input, XTypeSpec.Builder typeSpecBuilder) {
    XElement originatingElement = originatingElement(input);
    typeSpecBuilder
        .addOriginatingElement(originatingElement)
        .addAnnotation(XAnnotationSpec.of(XTypeNames.DAGGER_GENERATED));

    Optional<XAnnotationSpec> generatedAnnotation =
        findGeneratedAnnotation()
            .map(
                annotation ->
                    XAnnotationSpecs.builder(annotation.asClassName())
                        .addArrayMember("value", "%S", "dagger.internal.codegen.ComponentProcessor")
                        .addMember("comments", "%S", GENERATED_COMMENTS)
                        .build());
    generatedAnnotation.ifPresent(typeSpecBuilder::addAnnotation);

    // TODO(b/263891456): Remove KOTLIN_INTERNAL and use Object/raw types where necessary.
    typeSpecBuilder.addAnnotation(
        XAnnotationSpecs.suppressWarnings(
            ImmutableSet.<Suppression>builder()
                .addAll(warningSuppressions())
                .add(UNCHECKED, RAWTYPES, KOTLIN_INTERNAL, CAST, DEPRECATION, UNINITIALIZED)
                .build()));

    String packageName = closestEnclosingTypeElement(originatingElement).getPackageName();
    XFileSpec.Builder fileBuilder = XFileSpec.builder(packageName, typeSpecBuilder.build());
    toJavaPoet(fileBuilder).skipJavaLangImports(true);
    if (!generatedAnnotation.isPresent()) {
      fileBuilder.addFileComment("Generated by Dagger (%L).", GENERATED_COMMENTS);
    }
    return fileBuilder.build();
  }

  // TODO(b/392896762): Use XProcessingEnv.findGeneratedAnnotation() once we're on Kotlin 2.1.0
  private Optional<XTypeElement> findGeneratedAnnotation() {
    return Optional.ofNullable(
            processingEnv.findTypeElement("javax.annotation.processing.Generated"))
        .or(() -> Optional.ofNullable(processingEnv.findTypeElement("javax.annotation.Generated")));
  }

  /** Returns the originating element of the generating type. */
  public abstract XElement originatingElement(T input);

  /**
   * Returns {@link XTypeSpecs types} be generated for {@code T}, or an empty list if no types
   * should be generated.
   *
   * <p>Every type will be generated in its own file.
   */
  public abstract ImmutableList<XTypeSpec> topLevelTypes(T input);

  /** Returns {@link Suppression}s that are applied to files generated by this generator. */
  // TODO(b/134590785): When suppressions are removed locally, remove this and inline the usages
  protected ImmutableSet<Suppression> warningSuppressions() {
    return ImmutableSet.of();
  }
}
