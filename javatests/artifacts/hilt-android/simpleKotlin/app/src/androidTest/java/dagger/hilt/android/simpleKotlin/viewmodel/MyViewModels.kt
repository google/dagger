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

package dagger.hilt.android.simpleKotlin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.ViewModelInject
import javax.inject.Named

class MyAndroidViewModel(app: Application) : AndroidViewModel(app)

class MyViewModel() : ViewModel()

@Suppress("UNUSED_PARAMETER")
class MyInjectedViewModel @ViewModelInject constructor(
  foo: Foo,
  @Named("SayMyName") theName: String
) : ViewModel()

object TopClass {
  @Suppress("UNUSED_PARAMETER")
  class MyNestedInjectedViewModel @ViewModelInject constructor(foo: Foo) : ViewModel()
}

@Suppress("UNUSED_PARAMETER")
class MyInjectedViewModelWithSavedState @ViewModelInject constructor(
  foo: Foo,
  handle: SavedStateHandle
) : ViewModel()
