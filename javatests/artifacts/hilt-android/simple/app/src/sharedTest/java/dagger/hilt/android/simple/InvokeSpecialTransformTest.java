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

package dagger.hilt.android.simple;

import android.content.Context;
import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle.State;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test that verifies edge cases of invokespecial instructions transformation. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class InvokeSpecialTransformTest {

  @Rule
  public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void constructorCallOfOldSuperclass() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.moveToState(State.DESTROYED);
    }
  }

  /** A test activity */
  @AndroidEntryPoint
  public static final class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(new CustomView(this).createBrother());
    }
  }

  /** A custom view for testing */
  @AndroidEntryPoint
  public static class CustomView extends FrameLayout {

    public CustomView(@NonNull Context context) {
      // This super call is an invokespecial that should be transformed
      super(context);
      // This invokespecial that should not be transformed.
      FrameLayout secondInvokeSpecial = new FrameLayout(getContext());
    }

    FrameLayout createBrother() {
      // This invokespecial that should not be transformed.
      return new FrameLayout(getContext());
    }
  }
}