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

package dagger.functional.membersinject;

import java.util.List;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO: b/477601223 - Remove this test once this bug is fixed, as it should already be covered by
// MembersInjectionTest#membersInjectorSuperTypeWithInaccessibleTypeArgument().
@SuppressWarnings("unused") // This test is just used to check that the code compiles.
@RunWith(JUnit4.class)
public class MembersInjectTypeParameterWithVarianceTest {
  public static class Foo<T> {
    @Inject T t;
    @Inject List<T> listT;
    @Inject List<? extends T> listExtendsT;
    @Inject List<? extends T>[] arrayListExtendsT;

    @Inject
    void method(
        T t,
        List<T> listT,
        List<? extends T> listExtendsT,
        List<? extends T>[] arrayListExtendsT) {}
  }

  @Test
  public void testBuild() {
    // If this compiles, then this test is WAI.
  }
}
