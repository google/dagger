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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.truth0.Truth.ASSERT;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.squareup.javawriter.JavaWriter;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SourceFileGeneratorTest {
  private static final ClassName FAKE_CLASS_NAME = ClassName.create("test", "FakeClass");

  @Mock public Filer filer;
  @Mock public JavaFileObject file;
  @Mock public Writer writer;

  private SourceFileGenerator<Void> generator;

  @Before public void createGenerator() {
    this.generator = new FailingSourceFileGenerator(filer);
  }

  @Test public void generate_failToCreateFile() throws Exception {
    when(filer.createSourceFile(FAKE_CLASS_NAME.fullyQualifiedName()))
      .thenThrow(new IOException("file creation"));
    try {
      generator.generate(null);
      fail();
    } catch (SourceFileGenerationException e) {
      String message = e.getMessage();
      ASSERT.that(message).contains(FAKE_CLASS_NAME.fullyQualifiedName());
      ASSERT.that(message).contains("file creation");
    }
  }

  @Test public void generate_failToOpenWriter() throws Exception {
    when(filer.createSourceFile(FAKE_CLASS_NAME.fullyQualifiedName())).thenReturn(file);
    when(file.openWriter()).thenThrow(new IOException("opening writer"));
    try {
      generator.generate(null);
      fail();
    } catch (SourceFileGenerationException e) {
      String message = e.getMessage();
      ASSERT.that(message).contains(FAKE_CLASS_NAME.fullyQualifiedName());
      ASSERT.that(message).contains("opening writer");
    }
    verify(file).delete();
  }

  @Test public void generate_failToWrite() throws Exception {
    when(filer.createSourceFile(FAKE_CLASS_NAME.fullyQualifiedName())).thenReturn(file);
    when(file.openWriter()).thenReturn(writer);
    doThrow(new IOException("writing")).when(writer).write(anyString());
    try {
      generator.generate(null);
      fail();
    } catch (SourceFileGenerationException e) {
      String message = e.getMessage();
      ASSERT.that(message).contains(FAKE_CLASS_NAME.fullyQualifiedName());
      ASSERT.that(message).contains("writing");
    }
    verify(writer).close();
    verify(file).delete();
  }

  @Test public void generate_failToWriteFailToClose() throws Exception {
    when(filer.createSourceFile(FAKE_CLASS_NAME.fullyQualifiedName())).thenReturn(file);
    when(file.openWriter()).thenReturn(writer);
    doThrow(new IOException("writing")).when(writer).write(anyString());
    doThrow(new IOException("closing writer")).when(writer).close();
    try {
      generator.generate(null);
      fail();
    } catch (SourceFileGenerationException e) {
      String message = e.getMessage();
      ASSERT.that(message).contains(FAKE_CLASS_NAME.fullyQualifiedName());
      ASSERT.that(message).contains("writing");
    }
    verify(writer).close();
    verify(file).delete();
  }

  @Test public void generate_failToClose() throws Exception {
    when(filer.createSourceFile(FAKE_CLASS_NAME.fullyQualifiedName())).thenReturn(file);
    when(file.openWriter()).thenReturn(writer);
    doThrow(new IOException("closing writer")).when(writer).close();
    try {
      generator.generate(null);
      fail();
    } catch (SourceFileGenerationException e) {
      String message = e.getMessage();
      ASSERT.that(message).contains(FAKE_CLASS_NAME.fullyQualifiedName());
      ASSERT.that(message).contains("closing writer");
    }
    verify(writer).close();
    verify(file).delete();
  }

  private static final class FailingSourceFileGenerator extends SourceFileGenerator<Void> {
    FailingSourceFileGenerator(Filer filer) {
      super(filer);
    }

    @Override
    ClassName nameGeneratedType(Void input) {
      return FAKE_CLASS_NAME;
    }

    @Override
    Iterable<? extends Element> getOriginatingElements(Void input) {
      return ImmutableSet.of();
    }
    
    @Override
    Optional<? extends Element> getElementForErrorReporting(Void input) {
      return Optional.absent();
    }

    @Override
    void write(ClassName generatedTypeName, JavaWriter writer, Void input) throws IOException {
      writer.emitPackage(FAKE_CLASS_NAME.packageName())
          .beginType("class", FAKE_CLASS_NAME.simpleName())
          .endType();
    }
  }
}
