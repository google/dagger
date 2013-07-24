/**
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import javax.inject.Inject;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class PrimitiveInjectionTest {
  static class ArrayInjectable {
    @Inject byte[] byteArray;
    @Inject int[] integerArray;
    @Inject boolean[] booleanArray;
    @Inject char[] charArray;
    @Inject long[] longArray;
    @Inject float[] floatArray;
    @Inject double[] doubleArray;
  }

  @Module(injects = ArrayInjectable.class)
  static class PrimitiveArrayModule {
    @Provides byte[] byteArray() { return new byte[] { Byte.MAX_VALUE }; }
    @Provides int[] provideInt() { return new int[] { Integer.MAX_VALUE }; }
    @Provides boolean[] provideBoolean() { return new boolean[] { true }; }
    @Provides long[] provideLong() { return new long[] { Long.MAX_VALUE }; }
    @Provides char[] provideChar() { return new char[] { Character.MAX_VALUE }; }
    @Provides float[] provideFloat() { return new float[] { Float.MAX_VALUE }; }
    @Provides double[] provideDouble() { return new double[] { Double.MAX_VALUE }; }
  }

  @Test public void primitiveArrayTypesAllInjected() {
    ArrayInjectable result = ObjectGraph.create(PrimitiveArrayModule.class)
        .get(ArrayInjectable.class);
    assertThat(result).isNotNull();
    assertThat(result.byteArray).isEqualTo(new byte[] { Byte.MAX_VALUE });
    assertThat(result.integerArray).isEqualTo(new int[] { Integer.MAX_VALUE });
    assertThat(result.booleanArray).isEqualTo(new boolean[] { true });
    assertThat(result.charArray).isEqualTo(new char[] { Character.MAX_VALUE });
    assertThat(result.longArray).isEqualTo(new long[] { Long.MAX_VALUE });
    assertThat(result.floatArray).isEqualTo(new float[] { Float.MAX_VALUE });
    assertThat(result.doubleArray).isEqualTo(new double[] { Double.MAX_VALUE });
  }
}
