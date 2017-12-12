/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.producers.monitoring;

import static com.google.common.base.Preconditions.checkNotNull;

import dagger.producers.Produces;
import java.util.Objects;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A token that represents an individual {@linkplain Produces producer method}. */
public final class ProducerToken {
  @NullableDecl private final Class<?> classToken;
  @NullableDecl private final String methodName;

  private ProducerToken(@NullableDecl Class<?> classToken, @NullableDecl String methodName) {
    this.classToken = classToken;
    this.methodName = methodName;
  }

  /**
   * Creates a token for a class token that represents the generated factory for a producer method.
   *
   * <p><b>Do not use this!</b> This is intended to be called by generated code only, and its
   * signature may change at any time.
   */
  public static ProducerToken create(Class<?> classToken) {
    return new ProducerToken(checkNotNull(classToken), null);
  }

  /**
   * Creates a token for a producer method.
   *
   * <p><b>Do not use this!</b> This is intended to be called by generated code only, and its
   * signature may change at any time.
   */
  public static ProducerToken create(String methodName) {
    return new ProducerToken(null, checkNotNull(methodName));
  }

  /** Two tokens are equal if they represent the same method. */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof ProducerToken) {
      ProducerToken that = (ProducerToken) o;
      return Objects.equals(this.classToken, that.classToken)
          && Objects.equals(this.methodName, that.methodName);
    } else {
      return false;
    }
  }

  /** Returns an appropriate hash code to match {@link #equals(Object)}. */
  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= Objects.hashCode(this.classToken);
    h *= 1000003;
    h ^= Objects.hashCode(this.methodName);
    return h;
  }

  /** Returns a representation of the method. */
  @Override
  public String toString() {
    if (methodName != null) {
      return methodName;
    } else if (classToken != null) {
      return classToken.getCanonicalName();
    } else {
      throw new IllegalStateException();
    }
  }
}
