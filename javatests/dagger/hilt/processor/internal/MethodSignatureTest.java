/*
 * Copyright (C) 2026 The Dagger Authors.
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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MethodSignatureTest {

  @Test
  public void testBasicGetters() {
    MethodSignature signature = MethodSignature.of("foo", TypeName.INT, TypeName.BOOLEAN);
    assertThat(signature.name()).isEqualTo("foo");
    assertThat(signature.parameters()).containsExactly(TypeName.INT, TypeName.BOOLEAN).inOrder();
  }

  @Test
  public void testOfMethodSpec() {
    MethodSpec method = MethodSpec.methodBuilder("bar")
        .addParameter(ParameterSpec.builder(TypeName.INT, "x").build())
        .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "y").build())
        .build();
    MethodSignature signature = MethodSignature.of(method);
    assertThat(signature.name()).isEqualTo("bar");
    assertThat(signature.parameters()).containsExactly(TypeName.INT, TypeName.DOUBLE).inOrder();
  }

  @Test
  public void testEqualsAndHashCode() {
    MethodSignature sig1 = MethodSignature.of("foo", TypeName.INT);
    MethodSignature sig2 = MethodSignature.of("foo", TypeName.INT);
    MethodSignature sigDifferentParams = MethodSignature.of("foo", TypeName.BOOLEAN);
    MethodSignature sigDifferentName = MethodSignature.of("bar", TypeName.INT);

    // Equality
    assertThat(sig1).isEqualTo(sig2);
    assertThat(sig1).isNotEqualTo(sigDifferentParams);
    assertThat(sig1).isNotEqualTo(sigDifferentName);

    // HashCode contract (equal objects must have equal hashcodes)
    assertThat(sig1.hashCode()).isEqualTo(sig2.hashCode());

    // HashCode is name based to optimize initialization, so they can collide:
    assertThat(sig1.hashCode()).isEqualTo(sigDifferentParams.hashCode());
    assertThat(sig1.hashCode()).isNotEqualTo(sigDifferentName.hashCode());
  }

  @Test
  public void testEqualsShortCircuitsName() {
    MethodSignature sigThrowsOnParams = new ThrowingMethodSignature("foo");
    MethodSignature sigDifferentName = MethodSignature.of("bar", TypeName.INT);

    // Since names differ, equals() must short-circuit on the name and never invoke parameters()
    assertThat(sigThrowsOnParams.equals(sigDifferentName)).isFalse();

    // A standard equality check with same name should invoke parameters() and throw
    MethodSignature other = MethodSignature.of("foo", TypeName.INT);
    assertThrows(AssertionError.class, () -> sigThrowsOnParams.equals(other));
  }

  @Test
  public void testToString() {
    MethodSignature signature = MethodSignature.of("foo", TypeName.INT, TypeName.BOOLEAN);
    assertThat(signature.toString()).isEqualTo("foo(int,boolean)");
  }

  private static final class ThrowingMethodSignature extends MethodSignature {
    private final String name;

    ThrowingMethodSignature(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public ImmutableList<TypeName> parameters() {
      throw new AssertionError("Parameters should not be resolved!");
    }
  }
}
