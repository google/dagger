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

import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import dagger.MembersInjector;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test case for {@link FrameworkField}.
 */
@RunWith(JUnit4.class)
public class BindingFieldTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
  }

  private ExecutableElement getXConstructor() {
    TypeElement classElement = elements.getTypeElement(X.class.getCanonicalName());
    return Iterables.getOnlyElement(
        ElementFilter.constructorsIn(classElement.getEnclosedElements()));
  }

  @Test public void frameworkType() {
    Key key = keyFactory.forInjectConstructorWithResolvedType(
        getXConstructor().getEnclosingElement().asType());
    TypeName xClass = TypeNames.forTypeMirror(key.type());
    assertThat(FrameworkField.createWithTypeFromKey(Provider.class, key, "test").frameworkType())
        .isEqualTo(ParameterizedTypeName.create(ClassName.fromClass(Provider.class), xClass));
    assertThat(
            FrameworkField.createWithTypeFromKey(MembersInjector.class, key, "test")
                .frameworkType())
        .isEqualTo(
            ParameterizedTypeName.create(ClassName.fromClass(MembersInjector.class), xClass));
  }

  @Test public void nameSuffix() {
    Key key = keyFactory.forInjectConstructorWithResolvedType(
        getXConstructor().getEnclosingElement().asType());
    assertThat(FrameworkField.createWithTypeFromKey(Provider.class, key, "foo").name())
        .isEqualTo("fooProvider");
    assertThat(FrameworkField.createWithTypeFromKey(Provider.class, key, "fooProvider").name())
        .isEqualTo("fooProvider");

  }

  static final class X {
    @Inject X() {}
  }
}
