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

package dagger.functional.membersinject;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.functional.membersinject.subpackage.ExtendsMembersWithSameName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// https://github.com/google/dagger/issues/755
@RunWith(JUnit4.class)
public class MembersWithSameNameTest {
  @Test
  public void injectsMaskedMembers() {
    MembersWithSameName membersWithSameName = new MembersWithSameName();
    TestComponent component = DaggerMembersWithSameNameTest_TestComponent.create();
    component.inject(membersWithSameName);
    verifyBaseClassInjection(membersWithSameName);
  }

  @Test
  public void subclassInjectsMaskedMembers() {
    ExtendsMembersWithSameName extendsMembersWithSameName = new ExtendsMembersWithSameName();
    TestComponent component = DaggerMembersWithSameNameTest_TestComponent.create();
    component.inject(extendsMembersWithSameName);
    verifyBaseClassInjection(extendsMembersWithSameName);
    verifySubclassInjection(extendsMembersWithSameName);
  }

  private void verifyBaseClassInjection(MembersWithSameName membersWithSameName) {
    assertThat(membersWithSameName.sameName).isNotNull();
    assertThat(membersWithSameName.sameNameStringWasInvoked).isTrue();
    assertThat(membersWithSameName.sameNameObjectWasInvoked).isTrue();
  }

  private void verifySubclassInjection(ExtendsMembersWithSameName extendsMembersWithSameName) {
    assertThat(extendsMembersWithSameName.sameName()).isNotNull();
    assertThat(extendsMembersWithSameName.sameNameStringWasInvoked()).isTrue();
    assertThat(extendsMembersWithSameName.sameNameObjectWasInvoked()).isTrue();
  }

  @Module
  abstract static class TestModule {
    @Provides
    static String provideString() {
      return "";
    }

    @Binds
    abstract Object bindObject(String string);
  }

  @Component(modules = TestModule.class)
  interface TestComponent {
    void inject(MembersWithSameName membersWithSameName);
    void inject(ExtendsMembersWithSameName extendsMembersWithSameName);
  }
}
