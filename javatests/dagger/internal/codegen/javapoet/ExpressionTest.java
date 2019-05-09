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

package dagger.internal.codegen.javapoet;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.common.MoreTypes;
import com.google.testing.compile.CompilationRule;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExpressionTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();
  private DaggerElements elements;
  private DaggerTypes types;

  interface Supertype {}

  interface Subtype extends Supertype {}

  @Before
  public void setUp() {
    elements = new DaggerElements(compilationRule.getElements(), compilationRule.getTypes());
    types = new DaggerTypes(compilationRule.getTypes(), elements);
  }

  @Test
  public void castTo() {
    TypeMirror subtype = type(Subtype.class);
    TypeMirror supertype = type(Supertype.class);
    Expression expression = Expression.create(subtype, "new $T() {}", subtype);

    Expression castTo = expression.castTo(supertype);

    assertThat(castTo.type()).isSameInstanceAs(supertype);
    assertThat(castTo.codeBlock().toString())
        .isEqualTo(
            "(dagger.internal.codegen.javapoet.ExpressionTest.Supertype) "
                + "new dagger.internal.codegen.javapoet.ExpressionTest.Subtype() {}");
  }

  @Test
  public void box() {
    PrimitiveType primitiveInt = types.getPrimitiveType(TypeKind.INT);

    Expression primitiveExpression = Expression.create(primitiveInt, "5");
    Expression boxedExpression = primitiveExpression.box(types);

    assertThat(boxedExpression.codeBlock().toString()).isEqualTo("(java.lang.Integer) 5");
    assertThat(MoreTypes.equivalence().equivalent(boxedExpression.type(), type(Integer.class)))
        .isTrue();
  }

  private TypeMirror type(Class<?> clazz) {
    return elements.getTypeElement(clazz).asType();
  }
}
