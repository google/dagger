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

@file:JvmName("HiltViewModelExtensions")

package dagger.hilt.android.lifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import dagger.hilt.android.internal.lifecycle.HiltViewModelFactory

/**
 * Returns a new {@code CreationExtras} with the original entries plus the passed in creation
 * callback. The callback is used by Hilt to create {@link AssistedInject}-annotated {@link
 * HiltViewModel}s.
 *
 * @param callback A creation callback that takes an assisted factory and returns a {@code
 *   ViewModel}.
 */
fun <VMF> CreationExtras.withCreationCallback(callback: (VMF) -> ViewModel): CreationExtras =
  MutableCreationExtras(this).addCreationCallback(callback)

/**
 * Returns the {@code MutableCreationExtras} with the passed in creation callback added. The
 * callback is used by Hilt to create {@link AssistedInject}-annotated {@link HiltViewModel}s.
 *
 * @param callback A creation callback that takes an assisted factory and returns a {@code
 *   ViewModel}.
 */
@Suppress("UNCHECKED_CAST")
fun <VMF> MutableCreationExtras.addCreationCallback(callback: (VMF) -> ViewModel): CreationExtras =
  this.apply {
    this[HiltViewModelFactory.CREATION_CALLBACK_KEY] = { factory -> callback(factory as VMF) }
  }
