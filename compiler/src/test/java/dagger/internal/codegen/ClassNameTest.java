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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.CompilationRule;
import dagger.internal.codegen.ClassNameTest.OuterClass.InnerClass;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public class ClassNameTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Test public void bestGuessForString_simpleClass() {
    ASSERT.that(ClassName.bestGuessFromString(String.class.getName()))
        .isEqualTo(ClassName.create("java.lang", "String"));
  }

  static class OuterClass {
    static class InnerClass {}
  }

  @Test public void bestGuessForString_nestedClass() {
    ASSERT.that(ClassName.bestGuessFromString(Map.Entry.class.getCanonicalName()))
        .isEqualTo(ClassName.create("java.util", ImmutableList.of("Map"), "Entry"));
    ASSERT.that(ClassName.bestGuessFromString(OuterClass.InnerClass.class.getCanonicalName()))
        .isEqualTo(
            ClassName.create("dagger.internal.codegen",
                ImmutableList.of("ClassNameTest", "OuterClass"), "InnerClass"));
  }

  @Test public void bestGuessForString_defaultPackage() {
    ASSERT.that(ClassName.bestGuessFromString("SomeClass"))
        .isEqualTo(ClassName.create("", "SomeClass"));
    ASSERT.that(ClassName.bestGuessFromString("SomeClass.Nested"))
        .isEqualTo(ClassName.create("", ImmutableList.of("SomeClass"), "Nested"));
    ASSERT.that(ClassName.bestGuessFromString("SomeClass.Nested.EvenMore"))
        .isEqualTo(ClassName.create("", ImmutableList.of("SomeClass", "Nested"), "EvenMore"));
  }

  @Test public void bestGuessForString_confusingInput() {
    try {
      ClassName.bestGuessFromString("com.test.$");
      fail();
    } catch (IllegalArgumentException expected) {}
    try {
      ClassName.bestGuessFromString("com.test.LooksLikeAClass.pkg");
      fail();
    } catch (IllegalArgumentException expected) {}
    try {
      ClassName.bestGuessFromString("!@#$gibberish%^&*");
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test public void classNameFromTypeElement() {
    Elements elements = compilationRule.getElements();
    TypeElement element = elements.getTypeElement(Object.class.getCanonicalName());
    ASSERT.that(ClassName.fromTypeElement(element).fullyQualifiedName())
        .isEqualTo("java.lang.Object");
  }

  @Test public void peerNamed_topLevelClass() {
    Elements elements = compilationRule.getElements();
    TypeElement element = elements.getTypeElement(ClassNameTest.class.getCanonicalName());
    ClassName className = ClassName.fromTypeElement(element);
    ClassName peerName = className.peerNamed("Foo");
    ASSERT.that(peerName.fullyQualifiedName())
        .isEqualTo("dagger.internal.codegen.Foo");
  }

  @Test public void peerNamed_nestedClass() {
    Elements elements = compilationRule.getElements();
    TypeElement element = elements.getTypeElement(OuterClass.class.getCanonicalName());
    ClassName className = ClassName.fromTypeElement(element);
    ClassName peerName = className.peerNamed("Foo");
    ASSERT.that(peerName.fullyQualifiedName())
        .isEqualTo("dagger.internal.codegen.ClassNameTest.Foo");
  }

  @Test public void peerNamed_deeplyNestedClass() {
    Elements elements = compilationRule.getElements();
    TypeElement element = elements.getTypeElement(InnerClass.class.getCanonicalName());
    ClassName className = ClassName.fromTypeElement(element);
    ClassName peerName = className.peerNamed("Foo");
    ASSERT.that(peerName.fullyQualifiedName())
        .isEqualTo("dagger.internal.codegen.ClassNameTest.OuterClass.Foo");
  }

}
