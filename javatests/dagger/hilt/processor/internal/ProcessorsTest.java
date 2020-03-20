/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static com.google.common.truth.Truth.assertThat;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProcessorsTest {

  @Test
  public void testCopyMethodSpecWithoutBodyForMethod() throws Exception {
    TypeVariableName t = TypeVariableName.get("T", Number.class);
    ClassName list = ClassName.get(List.class);
    MethodSpec.Builder builder = MethodSpec.methodBuilder("myMethod")
        .addJavadoc("This is a test line 1 in the java doc \n"
            + "This is a test line 2 in the java doc \n"
            + "<p>Test Use of links {@link $T} \n", list)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterizedTypeName.get(list, t), "list", Modifier.FINAL)
        .returns(ParameterizedTypeName.get(list, t))
        .addException(IllegalArgumentException.class)
        .addTypeVariable(t);

    // Test that method copy is the same as original
    MethodSpec method = builder.build();
    MethodSpec methodCopy = Processors.copyMethodSpecWithoutBody(method).build();
    assertThat(method.toString()).contains(methodCopy.toString());

    // Test that method copy removes the body, compare with the old copy
    MethodSpec methodWithBody = builder.addStatement("return list").build();
    MethodSpec methodCopyWithBody = Processors.copyMethodSpecWithoutBody(methodWithBody).build();
    assertThat(methodCopyWithBody.toString()).isEqualTo(methodCopy.toString());

  }

  @Test
  public void testCopyMethodSpecWithoutBodyForConstructor() throws Exception {
    TypeVariableName t = TypeVariableName.get("T", Number.class);
    ClassName list = ClassName.get(List.class);
    MethodSpec.Builder builder = MethodSpec.constructorBuilder()
        .addJavadoc("This is a test line 1 in the java doc \n"
            + "This is a test line 2 in the java doc \n"
            + "<p>Test Use of links {@link $T} \n", list)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterizedTypeName.get(list, t), "list", Modifier.FINAL)
        .addException(IllegalArgumentException.class)
        .addTypeVariable(t);

    // Test that method copy is the same as original
    MethodSpec method = builder.build();
    MethodSpec methodCopy = Processors.copyMethodSpecWithoutBody(method).build();
    assertThat(method.toString()).contains(methodCopy.toString());

    // Test that method copy removes the body, compare with the old copy
    MethodSpec methodWithBody = builder.addStatement("this.list = list").build();
    MethodSpec methodCopyWithBody = Processors.copyMethodSpecWithoutBody(methodWithBody).build();
    assertThat(methodCopyWithBody.toString()).isEqualTo(methodCopy.toString());
  }
}
