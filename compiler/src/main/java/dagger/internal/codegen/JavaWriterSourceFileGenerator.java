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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.TypeWriter;
import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A template class that provides a framework for properly handling IO while generating source files
 * from an annotation processor.  Particularly, it makes a best effort to ensure that files that
 * fail to write successfully are deleted.
 *
 * @param <T> The input type from which source is to be generated.
 * @author Gregory Kick
 * @since 2.0
 */
abstract class JavaWriterSourceFileGenerator<T> implements SourceFileGenerator<T> {
  private final Filer filer;
  private final boolean generatedAnnotationAvailable;

  JavaWriterSourceFileGenerator(Filer filer, Elements elements) {
    this.filer = checkNotNull(filer);
    generatedAnnotationAvailable = elements.getTypeElement("javax.annotation.Generated") != null;
  }

  @Override
  public final void generate(T input) throws SourceFileGenerationException {
    ClassName generatedTypeName = nameGeneratedType(input);
    ImmutableSet<Element> originatingElements =
        ImmutableSet.<Element>copyOf(getOriginatingElements(input));
    try {
      ImmutableSet<JavaWriter> writers = write(generatedTypeName, input);
      for (JavaWriter javaWriter : writers) {
        javaWriter.markGenerated(generatedAnnotationAvailable);
        try {
          javaWriter.file(filer, originatingElements);
        } catch (IOException e) {
          throw new SourceFileGenerationException(getNamesForWriters(javaWriter.getTypeWriters()),
              e, getElementForErrorReporting(input));
        }
      }
    } catch (Exception e) {
      // if the code above threw a SFGE, use that
      Throwables.propagateIfPossible(e, SourceFileGenerationException.class);
      // otherwise, throw a new one
      throw new SourceFileGenerationException(ImmutableList.<ClassName>of(), e,
          getElementForErrorReporting(input));
    }
  }

  private static Iterable<ClassName> getNamesForWriters(Iterable<TypeWriter> typeWriters) {
    return Iterables.transform(typeWriters, new Function<TypeWriter, ClassName>() {
      @Override public ClassName apply(TypeWriter input) {
        return input.name();
      }
    });
  }

  /**
   * Implementations should return the {@link ClassName} for the top-level type to be generated.
   */
  abstract ClassName nameGeneratedType(T input);

  /**
   * Implementations should return {@link Element} instances from which the source is to be
   * generated.
   */
  abstract Iterable<? extends Element> getOriginatingElements(T input);

  /**
   * Returns an optional element to be used for reporting errors. This returns a single element
   * rather than a collection to reduce output noise.
   */
  abstract Optional<? extends Element> getElementForErrorReporting(T input);

  /**
   * Returns a set of writers to be generated for {@code T} input.
   */
  abstract ImmutableSet<JavaWriter> write(ClassName generatedTypeName, T input);
}
