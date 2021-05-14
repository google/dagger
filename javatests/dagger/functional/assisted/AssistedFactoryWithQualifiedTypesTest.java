/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.functional.assisted;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// See https://github.com/google/dagger/issues/2281
@RunWith(JUnit4.class)
public final class AssistedFactoryWithQualifiedTypesTest {
  @Component
  interface TestComponent {
    // Test a factory with duplicate types with unique qualifiers.
    DupeTypeFactory dupeTypeFactory();

    // Test a factory with duplicate qualifiers with unique types.
    DupeQualifierFactory dupeQualifierFactory();

    // Test a factory with unnecessary qualifiers on the factory.
    UnnecessaryQualifierFactory unnecessaryQualifierFactory();

    // Test a factory with different parameter order than the constructor.
    SwappedDupeTypeFactory swappedDupeTypeFactory();
  }

  static class DupeType {
    private final String str1;
    private final String str2;

    @AssistedInject
    DupeType(@Assisted("1") String str1, @Assisted("2") String str2) {
      this.str1 = str1;
      this.str2 = str2;
    }
  }

  @AssistedFactory
  interface DupeTypeFactory {
    DupeType create(@Assisted("1") String str1, @Assisted("2") String str2);
  }

  @Test
  public void testDupeTypeFactory() {
    String str1 = "str1";
    String str2 = "str2";
    DupeType dupeType =
        DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
            .dupeTypeFactory()
            .create(str1, str2);
    assertThat(dupeType.str1).isEqualTo(str1);
    assertThat(dupeType.str2).isEqualTo(str2);
  }

  @AssistedFactory
  interface SwappedDupeTypeFactory {
    DupeType create(@Assisted("2") String str2, @Assisted("1") String str1);
  }

  @Test
  public void testSwappedDupeTypeFactory() {
    String str1 = "str1";
    String str2 = "str2";
    DupeType dupeType =
        DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
            .swappedDupeTypeFactory()
            .create(str2, str1);
    assertThat(dupeType.str1).isEqualTo(str1);
    assertThat(dupeType.str2).isEqualTo(str2);
  }

  static class DupeQualifier {
    private final String str;
    private final int i;

    @AssistedInject
    DupeQualifier(@Assisted("1") String str, @Assisted("1") int i) {
      this.str = str;
      this.i = i;
    }
  }

  @AssistedFactory
  interface DupeQualifierFactory {
    DupeQualifier create(@Assisted("1") String str, @Assisted("1") int i);
  }

  @Test
  public void testDupeQualifierFactory() {
    String str = "str";
    int i = 11;
    DupeQualifier dupeQualifier =
        DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
            .dupeQualifierFactory()
            .create(str, i);
    assertThat(dupeQualifier.str).isEqualTo(str);
    assertThat(dupeQualifier.i).isEqualTo(i);
  }

  static class UnnecessaryQualifier {
    private final String str;
    private final double d;
    private final int i;

    @AssistedInject
    UnnecessaryQualifier(@Assisted String str, @Assisted double d, @Assisted("") int i) {
      this.str = str;
      this.d = d;
      this.i = i;
    }
  }

  @AssistedFactory
  interface UnnecessaryQualifierFactory {
    UnnecessaryQualifier create(@Assisted int i, @Assisted("") String str, double d);
  }

  @Test
  public void testUnnecessaryQualifierFactory() {
    String str = "str";
    double d = 2.2;
    int i = 11;
    UnnecessaryQualifier unnecessaryQualifier =
        DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
            .unnecessaryQualifierFactory()
            .create(i, str, d);
    assertThat(unnecessaryQualifier.str).isEqualTo(str);
    assertThat(unnecessaryQualifier.d).isEqualTo(d);
    assertThat(unnecessaryQualifier.i).isEqualTo(i);
  }
}
