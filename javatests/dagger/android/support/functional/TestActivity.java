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

import android.os.Bundle;
import dagger.android.support.DaggerAppCompatActivity;
import java.util.Set;
import javax.inject.Inject;

public final class TestActivity extends DaggerAppCompatActivity {
  @Inject Set<Class<?>> componentHierarchy;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_layout);

    getSupportFragmentManager()
        .beginTransaction()
        .add(new TestParentFragment(), "parent-fragment")
        .add(new TestDialogFragment(), "dialog-fragment")
        .commit();
  }
}
