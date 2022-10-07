/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.getPrimitiveIntType;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.testing.compile.CompilationRule;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.internal.codegen.javac.JavacPluginModule;
import dagger.internal.codegen.javapoet.Expression;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExpressionTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Inject XProcessingEnv processingEnv;

  interface Supertype {}

  interface Subtype extends Supertype {}

  @Before
  public void setUp() {
    DaggerExpressionTest_TestComponent.builder()
        .javacPluginModule(
            new JavacPluginModule(compilationRule.getElements(), compilationRule.getTypes()))
        .build()
        .inject(this);
  }

  @Test
  public void castTo() {
    XType subtype = type(Subtype.class);
    XType supertype = type(Supertype.class);
    Expression expression = Expression.create(subtype, "new $T() {}", subtype.getTypeName());

    Expression castTo = expression.castTo(supertype);

    assertThat(castTo.type().getTypeName()).isEqualTo(supertype.getTypeName());
    assertThat(castTo.codeBlock().toString())
        .isEqualTo(
            "(dagger.internal.codegen.ExpressionTest.Supertype) "
                + "new dagger.internal.codegen.ExpressionTest.Subtype() {}");
  }

  @Test
  public void box() {
    XType primitiveInt = getPrimitiveIntType(processingEnv);

    Expression primitiveExpression = Expression.create(primitiveInt, "5");
    Expression boxedExpression = primitiveExpression.box();

    assertThat(boxedExpression.codeBlock().toString()).isEqualTo("(java.lang.Integer) 5");
    assertThat(boxedExpression.type().getTypeName()).isEqualTo(type(Integer.class).getTypeName());
  }

  private XType type(Class<?> clazz) {
    return processingEnv.requireType(ClassName.get(clazz));
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    void inject(ExpressionTest test);
  }
}
