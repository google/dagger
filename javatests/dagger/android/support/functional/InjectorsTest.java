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

package dagger.android.support.functional;

import static com.google.common.truth.Truth.assertThat;

import org.robolectric.RobolectricTestRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class InjectorsTest {
  private static final String MANIFEST =
      "//javatests/dagger/android/support/functional"
          + ":functional/AndroidManifest.xml";
  private TestActivity activity;
  private TestParentFragment parentFragment;
  private TestChildFragment childFragment;

  @Before
  public void setUp() {
    activity = Robolectric.setupActivity(TestActivity.class);
    parentFragment =
        (TestParentFragment)
            activity.getSupportFragmentManager().findFragmentByTag("parent-fragment");
    childFragment =
        (TestChildFragment)
            parentFragment.getChildFragmentManager().findFragmentByTag("child-fragment");
  }

  @Test
  @Config(
    manifest = MANIFEST,
    application = ComponentStructureFollowsControllerStructureApplication.class
  )
  public void componentStructureFollowsControllerStructure() {
    assertThat(activity.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.class);
    assertThat(parentFragment.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.ParentFragmentSubcomponent.class);
    assertThat(childFragment.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.ParentFragmentSubcomponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.ParentFragmentSubcomponent.ChildFragmentSubcomponent.class);
  }

  @Test
  @Config(manifest = MANIFEST, application = AllControllersAreDirectChildrenOfApplication.class)
  public void AllControllersAreDirectChildrenOfApplication() {
    assertThat(activity.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.ActivitySubcomponent
                .class);
    assertThat(parentFragment.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .ParentFragmentSubcomponent.class);
    assertThat(childFragment.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .ChildFragmentSubcomponent.class);
  }
}
