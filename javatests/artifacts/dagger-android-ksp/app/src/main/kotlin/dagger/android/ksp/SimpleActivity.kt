/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.android.ksp

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.android.support.DaggerAppCompatActivity
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * The main activity of the application.
 *
 * <p>It can be injected with any binding from both {@link SimpleActivityComponent} and {@link
 * SimpleApplication.SimpleComponent}.
 */
class SimpleActivity : DaggerAppCompatActivity() {
  private val TAG: String = SimpleActivity::class.java.getSimpleName()

  @Inject @UserName lateinit var userName: String
  @Inject @Model lateinit var model: String

  override protected fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "Injected with userName and model: " + userName + ", " + model)

    setContentView(R.layout.activity_main)

    val greeting = findViewById(R.id.greeting) as TextView
    val text = getResources().getString(R.string.welcome, userName, model)
    greeting.setText(text)
  }
}

@Subcomponent
interface SimpleActivityComponent : AndroidInjector<SimpleActivity> {

  @Subcomponent.Factory interface Factory : AndroidInjector.Factory<SimpleActivity> {}
}

@Module(subcomponents = [SimpleActivityComponent::class], includes = [UserNameModule::class])
interface InjectorModule {
  @Binds
  @IntoMap
  @ClassKey(SimpleActivity::class)
  fun bind(factory: SimpleActivityComponent.Factory): AndroidInjector.Factory<*>
}

