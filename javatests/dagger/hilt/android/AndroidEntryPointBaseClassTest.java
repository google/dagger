/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import androidx.activity.ComponentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.other.pkg.AndroidEntryPointBaseClassOtherPkg;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Regression test for https://github.com/google/dagger/issues/1910
 *
 * <p>There are 8 different tests to cover 3 levels of inheritance where each level uses either the
 * long-form (L) or short-form (S) of @AndroidEntryPoint:
 *
 * <ol>
 *   <li> L -> L -> L
 *   <li> L -> L -> S
 *   <li> L -> S -> L
 *   <li> L -> S -> S
 *   <li> S -> L -> L
 *   <li> S -> L -> S
 *   <li> S -> S -> L
 *   <li> S -> S -> S
 * </ol>
 *
 * Note: We don't actually test injection in this class because Bazel doesn't do bytecode injection.
 * We're only testing that the classes build, and verifying their inheritance matches what we
 * expect.
 */
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P)
public final class AndroidEntryPointBaseClassTest {

  @AndroidEntryPoint
  public static final class SSActivity extends AndroidEntryPointBaseClassOtherPkg.SBaseActivity {}

  @AndroidEntryPoint
  public static final class SLActivity extends AndroidEntryPointBaseClassOtherPkg.LBaseActivity {}

  @AndroidEntryPoint(AndroidEntryPointBaseClassOtherPkg.SBaseActivity.class)
  public static final class LSActivity extends Hilt_AndroidEntryPointBaseClassTest_LSActivity {}

  @AndroidEntryPoint(AndroidEntryPointBaseClassOtherPkg.LBaseActivity.class)
  public static final class LLActivity extends Hilt_AndroidEntryPointBaseClassTest_LLActivity {}

  @AndroidEntryPoint(LL.class)
  public static final class LLL extends Hilt_AndroidEntryPointBaseClassTest_LLL {}

  @AndroidEntryPoint(LS.class)
  public static final class LLS extends Hilt_AndroidEntryPointBaseClassTest_LLS {}

  @AndroidEntryPoint(SL.class)
  public static final class LSL extends Hilt_AndroidEntryPointBaseClassTest_LSL {}

  @AndroidEntryPoint(SS.class)
  public static final class LSS extends Hilt_AndroidEntryPointBaseClassTest_LSS {}

  @AndroidEntryPoint
  public static final class SLL extends LL {}

  @AndroidEntryPoint
  public static final class SLS extends LS {}

  @AndroidEntryPoint
  public static final class SSL extends SL {}

  @AndroidEntryPoint
  public static final class SSS extends SS {}

  @AndroidEntryPoint(L.class)
  public static class LL extends Hilt_AndroidEntryPointBaseClassTest_LL {}

  @AndroidEntryPoint(S.class)
  public static class LS extends Hilt_AndroidEntryPointBaseClassTest_LS {}

  @AndroidEntryPoint
  public static class SL extends L {}

  @AndroidEntryPoint
  public static class SS extends S {}

  @AndroidEntryPoint(ComponentActivity.class)
  public static class L extends Hilt_AndroidEntryPointBaseClassTest_L {}

  @AndroidEntryPoint
  public static class S extends ComponentActivity {}

  @Test
  public void checkGeneratedClassHierarchy_shortForm() throws Exception {
    // When using the short form notation, the generated top level class is not actually assignable
    // to the generated base classes at compile time
    assertIsNotAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_SSS.class,
        Hilt_AndroidEntryPointBaseClassTest_S.class);
    assertIsNotAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_SS.class,
        Hilt_AndroidEntryPointBaseClassTest_S.class);
  }

  @Test
  public void checkGeneratedClassHierarchy_longForm() throws Exception {
    // When using the long form notation, they are assignable at compile time
    assertIsAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_LLL.class,
        Hilt_AndroidEntryPointBaseClassTest_LL.class);
    assertIsAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_LL.class,
        Hilt_AndroidEntryPointBaseClassTest_L.class);
  }

  @Test
  public void checkGeneratedClassHierarchy_shortFormRoot() throws Exception {
    // If the root is short-form, then the child class cannot be assigned to it.
    assertIsNotAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_LLS.class,
        Hilt_AndroidEntryPointBaseClassTest_S.class);
    assertIsNotAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_LS.class,
        Hilt_AndroidEntryPointBaseClassTest_S.class);
  }

  @Test
  public void checkGeneratedClassHierarchy_longFormRoot() throws Exception {
    // If the root is long-form, then the child class can be assigned to it.
    assertIsAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_SSL.class,
        Hilt_AndroidEntryPointBaseClassTest_L.class);
    assertIsAssignableTo(
        Hilt_AndroidEntryPointBaseClassTest_SL.class,
        Hilt_AndroidEntryPointBaseClassTest_L.class);
  }

  /** Asserts that the {@code class1} is not assignable to the {@code class2}. */
  private static void assertIsNotAssignableTo(Class<?> class1, Class<?> class2) {
    assertThat(class2.isAssignableFrom(class1)).isFalse();
  }

  /** Asserts that the {@code class1} is assignable to the {@code class2}. */
  private static void assertIsAssignableTo(Class<?> class1, Class<?> class2) {
    assertThat(class1).isAssignableTo(class2);
  }
}
