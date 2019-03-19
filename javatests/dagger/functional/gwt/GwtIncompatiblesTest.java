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

package dagger.functional.gwt;

import dagger.functional.gwt.GwtIncompatibles.GwtIncompatible;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@code @GwtIncompatible} bindings. */
@RunWith(JUnit4.class)
public class GwtIncompatiblesTest {
  @Test
  public void testIncompatible() {
    assertGwtIncompatible(GwtIncompatibles_OnClass_Factory.class);
    assertGwtIncompatible(GwtIncompatibles_OnConstructor_Factory.class);
    assertGwtIncompatible(GwtIncompatibles_OuterClass_OnOuterClass_Factory.class);

    assertGwtIncompatible(GwtIncompatibles_MembersInjectedType_MembersInjector.class);

    assertGwtIncompatible(GwtIncompatibles_OnModule_OnModuleFactory.class);
    assertGwtIncompatible(GwtIncompatibles_OnMethod_OnMethodFactory.class);
  }

  private void assertGwtIncompatible(Class<?> clazz) {
    boolean gwtIncompatible = clazz.isAnnotationPresent(GwtIncompatible.class);
    if (!gwtIncompatible) {
      throw new AssertionError(clazz.getCanonicalName() + " is not @GwtIncompatible");
    }
  }
}
