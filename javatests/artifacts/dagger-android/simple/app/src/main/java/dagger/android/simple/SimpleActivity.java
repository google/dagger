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

package dagger.android.simple;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.android.AndroidInjector;
import dagger.android.support.DaggerAppCompatActivity;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import javax.inject.Inject;

/**
 * The main activity of the application.
 *
 * <p>It can be injected with any binding from both {@link SimpleActivityComponent} and {@link
 * SimpleApplication.SimpleComponent}.
 */
public class SimpleActivity extends DaggerAppCompatActivity {
  @Subcomponent
  interface SimpleActivityComponent extends AndroidInjector<SimpleActivity> {

    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<SimpleActivity> {}
  }

  @Module(
      subcomponents = SimpleActivityComponent.class,
      includes = UserNameModule.class
  )
  abstract static class InjectorModule {

    @Binds
    @IntoMap
    @ClassKey(SimpleActivity.class)
    abstract AndroidInjector.Factory<?> bind(SimpleActivityComponent.Factory factory);
  }

  private static final String TAG = SimpleActivity.class.getSimpleName();

  @Inject @UserName String userName;
  @Inject @Model String model;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "Injected with userName and model: " + userName + ", " + model);

    setContentView(R.layout.activity_main);

    TextView greeting = (TextView) findViewById(R.id.greeting);
    String text = getResources().getString(R.string.welcome, userName, model);
    greeting.setText(text);
  }
}
