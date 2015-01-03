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
package dagger.internal.codegen.writer;

import com.google.testing.compile.CompilationRule;
import java.io.IOException;
import java.util.Collections;
import javax.lang.model.element.Element;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;

@RunWith(JUnit4.class)
public class JavaWriterTest {

  @Test public void referencedAndDeclaredSimpleName() throws IOException {
    JavaWriter javaWriter = JavaWriter.inPackage("test");
    ClassWriter topClass = javaWriter.addClass("Top");
    topClass.addNestedClass("Middle").addNestedClass("Bottom");
    topClass.addField(ClassName.create("some.other.pkg", "Bottom"), "field");

    String source = javaWriter.write(new StringBuilder(), Collections.<Element>emptySet()).toString();

    assert_().that(source).contains("some.other.pkg.Bottom field;");
  }

  @Rule public final CompilationRule compilation = new CompilationRule();

  interface Top {
    interface Foo {
    }
  }

  interface Middle extends Top {
    interface Foo {
    }
  }

  interface Bottom extends Middle {
    interface Foo {
    }
  }

  @Test public void enclosedTypes() {
    assert_().that(JavaWriter.enclosedTypes(originatingElements(Top.class)))
        .containsExactly(className(Top.Foo.class));

    assert_().that(JavaWriter.enclosedTypes(originatingElements(Middle.class)))
        .containsExactly(className(Middle.Foo.class), className(Top.Foo.class));

    assert_().that(JavaWriter.enclosedTypes(originatingElements(Bottom.class)))
        .containsExactly(className(Bottom.Foo.class), className(Middle.Foo.class), className(Top.Foo.class));
  }

  private Iterable<? extends Element> originatingElements(Class<?> clazz) {
    return Collections.singleton(compilation.getElements().getTypeElement(clazz.getCanonicalName()));
  }

  private ClassName className(Class clazz) {
    return ClassName.bestGuessFromString(clazz.getCanonicalName());
  }
}
