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
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClassNameTest {
  @Test public void bestGuessForString_simpleClass() {
    ASSERT.that(ClassName.bestGuessFromString(String.class.getName()))
        .isEqualTo(ClassName.create("java.lang", "String"));
  }

  @Test public void bestGuessForString_nestedClass() {
    ASSERT.that(ClassName.bestGuessFromString(Map.Entry.class.getCanonicalName()))
        .isEqualTo(ClassName.create("java.util", ImmutableList.of("Map"), "Entry"));
    ASSERT.that(ClassName.bestGuessFromString(ProcessBuilder.Redirect.Type.class.getCanonicalName()))
        .isEqualTo(
            ClassName.create("java.lang", ImmutableList.of("ProcessBuilder", "Redirect"), "Type"));
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
}
