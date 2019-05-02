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

import android.content.Intent;
import android.content.res.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class InjectorsTest {
  private ActivityController<TestActivity> activityController;
  private TestActivity activity;
  private TestParentFragment parentFragment;
  private TestChildFragment childFragment;
  private TestDialogFragment dialogFragment;
  private TestService service;
  private TestIntentService intentService;
  private TestBroadcastReceiver broadcastReceiver;
  private TestContentProvider contentProvider;

  @Before
  public void setUp() {
    activityController = Robolectric.buildActivity(TestActivity.class);
    activity = activityController.setup().get();
    parentFragment =
        (TestParentFragment)
            activity.getSupportFragmentManager().findFragmentByTag("parent-fragment");
    childFragment =
        (TestChildFragment)
            parentFragment.getChildFragmentManager().findFragmentByTag("child-fragment");
    dialogFragment =
        (TestDialogFragment)
            activity.getSupportFragmentManager().findFragmentByTag("dialog-fragment");

    service = Robolectric.buildService(TestService.class).create().get();
    intentService = Robolectric.buildIntentService(TestIntentService.class).create().get();

    broadcastReceiver = new TestBroadcastReceiver();
    broadcastReceiver.onReceive(RuntimeEnvironment.application, new Intent());

    contentProvider = Robolectric.setupContentProvider(TestContentProvider.class);
  }

  @Test
  @Config(application = ComponentStructureFollowsControllerStructureApplication.class)
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
    assertThat(dialogFragment.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ActivitySubcomponent.DialogFragmentSubcomponent.class);

    assertThat(service.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ServiceSubcomponent.class);
    assertThat(intentService.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .IntentServiceSubcomponent.class);

    assertThat(broadcastReceiver.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .BroadcastReceiverSubcomponent.class);

    assertThat(contentProvider.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .ContentProviderSubcomponent.class);

    changeConfiguration();

    OuterClass.TestInnerClassActivity innerClassActivity =
        Robolectric.setupActivity(OuterClass.TestInnerClassActivity.class);
    assertThat(innerClassActivity.componentHierarchy)
        .containsExactly(
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent.class,
            ComponentStructureFollowsControllerStructureApplication.ApplicationComponent
                .InnerActivitySubcomponent.class);
  }

  @Test
  @Config(application = AllControllersAreDirectChildrenOfApplication.class)
  public void allControllersAreDirectChildrenOfApplication() {
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
    assertThat(dialogFragment.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .DialogFragmentSubcomponent.class);

    assertThat(service.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.ServiceSubcomponent
                .class);
    assertThat(intentService.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .IntentServiceSubcomponent.class);

    assertThat(broadcastReceiver.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .BroadcastReceiverSubcomponent.class);

    assertThat(contentProvider.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .ContentProviderSubcomponent.class);

    changeConfiguration();

    OuterClass.TestInnerClassActivity innerClassActivity =
        Robolectric.setupActivity(OuterClass.TestInnerClassActivity.class);
    assertThat(innerClassActivity.componentHierarchy)
        .containsExactly(
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent.class,
            AllControllersAreDirectChildrenOfApplication.ApplicationComponent
                .InnerActivitySubcomponent.class);
  }

  @Test
  @Config(application = UsesGeneratedModulesApplication.class)
  public void usesGeneratedModules() {
    assertThat(activity.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyActivitySubcomponent.class);
    assertThat(parentFragment.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyParentFragmentSubcomponent.class);
    assertThat(childFragment.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyChildFragmentSubcomponent.class);
    assertThat(dialogFragment.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyDialogFragmentSubcomponent.class);

    assertThat(service.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyServiceSubcomponent.class);
    assertThat(intentService.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyIntentServiceSubcomponent.class);

    assertThat(broadcastReceiver.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyBroadcastReceiverSubcomponent.class);

    assertThat(contentProvider.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyContentProviderSubcomponent.class);

    changeConfiguration();

    TestActivityWithScope activityWithScope =
        Robolectric.setupActivity(TestActivityWithScope.class);
    assertThat(activityWithScope.scopedStringProvider.get())
        .isSameInstanceAs(activityWithScope.scopedStringProvider.get());

    OuterClass.TestInnerClassActivity innerClassActivity =
        Robolectric.setupActivity(OuterClass.TestInnerClassActivity.class);
    assertThat(innerClassActivity.componentHierarchy)
        .containsExactly(
            UsesGeneratedModulesApplication.ApplicationComponent.class,
            UsesGeneratedModulesApplication.DummyInnerActivitySubcomponent.class);
  }

  // https://github.com/google/dagger/issues/598
  private void changeConfiguration() {
    Configuration oldConfiguration = activity.getResources().getConfiguration();
    Configuration newConfiguration = new Configuration(oldConfiguration);
    newConfiguration.orientation =
        oldConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
            ? Configuration.ORIENTATION_PORTRAIT
            : Configuration.ORIENTATION_LANDSCAPE;
    activityController.configurationChange(newConfiguration);
  }
}
