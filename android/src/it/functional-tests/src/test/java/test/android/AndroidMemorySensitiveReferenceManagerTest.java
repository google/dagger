/*
 * Copyright (C) 2016 The Dagger Authors.
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

package test.android;

import static android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
import static com.google.common.truth.Truth.assertThat;
import static test.android.AndroidMemorySensitiveReferenceManagerTest.AllWeakReferencesCleared.allWeakReferencesCleared;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.GcFinalization;
import com.google.common.testing.GcFinalization.FinalizationPredicate;
import java.lang.ref.WeakReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Functional tests of {@link dagger.android.AndroidMemorySensitiveReferenceManager}. */
@RunWith(JUnit4.class)
public final class AndroidMemorySensitiveReferenceManagerTest {

  private TestModule testModule;
  private TestComponent component;

  @Before
  public void setUp() {
    testModule = new TestModule();
    component = DaggerTestComponent.builder().testModule(testModule).build();
  }

  @Test
  public void scoped() {
    assertThat(component.releasedWhenUiHidden()).isSameAs(component.releasedWhenUiHidden());
    assertThat(component.releasedWhenModerate()).isSameAs(component.releasedWhenModerate());
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(1);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);
  }

  @Test
  public void onTrimMemory_aboveThresholds() {
    component.releasedWhenUiHidden();
    component.releasedWhenModerate();

    component.manager().onTrimMemory(TRIM_MEMORY_COMPLETE);
    GcFinalization.awaitDone(
        allWeakReferencesCleared(
            component.releasedWhenUiHidden(), component.releasedWhenModerate()));

    assertThat(component.releasedWhenUiHidden()).isSameAs(component.releasedWhenUiHidden());
    assertThat(component.releasedWhenModerate()).isSameAs(component.releasedWhenModerate());
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(2);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(2);
  }

  @Test
  public void onTrimMemory_atOneThresholdBelowAnother() {
    component.releasedWhenUiHidden();
    component.releasedWhenModerate();

    component.manager().onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
    GcFinalization.awaitDone(allWeakReferencesCleared(component.releasedWhenUiHidden()));

    assertThat(component.releasedWhenUiHidden()).isSameAs(component.releasedWhenUiHidden());
    assertThat(component.releasedWhenModerate()).isSameAs(component.releasedWhenModerate());
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(2);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);
  }

  @Test
  public void onTrimMemory_belowThresholds() {
    component.releasedWhenUiHidden();
    component.releasedWhenModerate();
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(1);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);

    component.manager().onTrimMemory(TRIM_MEMORY_RUNNING_MODERATE);
    GcFinalization.awaitDone(allWeakReferencesCleared(new Object()));

    assertThat(component.releasedWhenUiHidden()).isSameAs(component.releasedWhenUiHidden());
    assertThat(component.releasedWhenModerate()).isSameAs(component.releasedWhenModerate());
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(1);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);
  }

  @Test
  public void onTrimMemory_restore() {
    component.releasedWhenUiHidden();
    component.releasedWhenModerate();
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(1);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);

    component.manager().onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
    component.manager().onTrimMemory(TRIM_MEMORY_RUNNING_MODERATE);
    GcFinalization.awaitDone(allWeakReferencesCleared(new Object()));

    assertThat(component.releasedWhenUiHidden()).isSameAs(component.releasedWhenUiHidden());
    assertThat(component.releasedWhenModerate()).isSameAs(component.releasedWhenModerate());
    assertThat(testModule.releasedWhenUiHiddenCalls).isEqualTo(1);
    assertThat(testModule.releasedWhenModerateCalls).isEqualTo(1);
  }

  static final class AllWeakReferencesCleared implements FinalizationPredicate {

    private final ImmutableList<WeakReference<Object>> references;

    AllWeakReferencesCleared(ImmutableList<WeakReference<Object>> references) {
      this.references = references;
    }

    @Override
    public boolean isDone() {
      for (WeakReference<Object> reference : references) {
        if (reference.get() != null) {
          return false;
        }
      }
      return true;
    }

    static AllWeakReferencesCleared allWeakReferencesCleared(Object... objects) {
      ImmutableList.Builder<WeakReference<Object>> referencesBuilder = ImmutableList.builder();
      for (Object object : objects) {
        referencesBuilder.add(new WeakReference<>(object));
      }
      return new AllWeakReferencesCleared(referencesBuilder.build());
    }
  }
}
