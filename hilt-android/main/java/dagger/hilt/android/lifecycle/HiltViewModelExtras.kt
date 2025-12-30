/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.hilt.android.lifecycle

import androidx.lifecycle.viewmodel.CreationExtras
import dagger.hilt.android.lifecycle.HiltViewModelExtras.Key
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A map-like object holding pairs of [HiltViewModelExtras.Key] and [Any], enabling efficient value
 * retrieval for each key. Each key in [HiltViewModelExtras] is unique, storing only one value per
 * key.
 *
 * [HiltViewModelExtras] are bound in the [dagger.hilt.android.components.ViewModelComponent] if set
 * in the view model's [CreationExtras] with [HiltViewModelExtras.CreationExtrasKey].
 *
 * This abstract class supports read-only access; use [MutableHiltViewModelExtras] for read-write
 * access.
 */
abstract class HiltViewModelExtras internal constructor() {
  internal val extras: MutableMap<Key<*>, Any?> = mutableMapOf()

  /**
   * Key for the elements of [HiltViewModelExtras]. [T] represents the type of element associated
   * with this key.
   */
  interface Key<T>

  /**
   * Returns the value to which the specified [key] is associated, or null if this
   * [HiltViewModelExtras] contains no mapping for the key.
   */
  abstract operator fun <T> get(key: Key<T>): T?

  /** Compares the specified object with this [HiltViewModelExtras] for equality. */
  override fun equals(other: Any?): Boolean = other is HiltViewModelExtras && extras == other.extras

  /** Returns the hash code value for this [HiltViewModelExtras]. */
  override fun hashCode(): Int = extras.hashCode()

  /**
   * Returns a string representation of this [HiltViewModelExtras]. The string representation
   * consists of a list of key-value mappings in the order returned by the [HiltViewModelExtras]'s
   * iterator.
   */
  override fun toString(): String = "HiltViewModelExtras(extras=$extras)"

  /** An empty read-only [HiltViewModelExtras]. */
  object Empty : HiltViewModelExtras() {
    override fun <T> get(key: Key<T>): T? = null
  }

  companion object {
    /** The [CreationExtras.Key] used to store a [HiltViewModelExtras] in [CreationExtras]. */
    @JvmField val HILT_VIEW_MODEL_EXTRAS_KEY = CreationExtras.Key<HiltViewModelExtras>()

    /** Returns a unique [Key] to be associated with an extra. */
    @JvmStatic inline fun <reified T> Key(): Key<T> = object : Key<T> {}
  }
}

/**
 * A modifiable [HiltViewModelExtras] that holds pairs of [HiltViewModelExtras.Key] and [Any],
 * allowing efficient value retrieval for each key.
 *
 * Each key in [HiltViewModelExtras] is unique, storing only one value per key.
 *
 * @see [HiltViewModelExtras]
 */
class MutableHiltViewModelExtras
/**
 * Constructs a [MutableHiltViewModelExtras] containing the elements of the specified
 * [initialExtras], in the order they are returned by the [Map]'s iterator.
 */
internal constructor(initialExtras: Map<Key<*>, Any?>) : HiltViewModelExtras() {

  /**
   * Constructs a [MutableHiltViewModelExtras] containing the elements of the specified
   * [initialExtras], in the order they are returned by the [HiltViewModelExtras]'s iterator.
   */
  @JvmOverloads constructor(initialExtras: HiltViewModelExtras = Empty) : this(initialExtras.extras)

  init {
    extras += initialExtras
  }

  /** Associates the specified [t] with the specified [key] in this [HiltViewModelExtras]. */
  operator fun <T> set(key: Key<T>, t: T) {
    extras[key] = t
  }

  /**
   * Returns the value to which the specified [key] is associated, or null if this
   * [HiltViewModelExtras] contains no mapping for the key.
   */
  @Suppress("UNCHECKED_CAST") override fun <T> get(key: Key<T>): T? = extras[key] as T?
}

/**
 * Checks if the [HiltViewModelExtras] contains the given [key].
 *
 * This method allows to use the `key in hiltViewModelExtras` syntax for checking whether an [key]
 * is contained in the [HiltViewModelExtras].
 */
operator fun HiltViewModelExtras.contains(key: Key<*>): Boolean = key in extras

/**
 * Creates a new read-only [HiltViewModelExtras] by replacing or adding entries to [this] extras
 * from another [hiltViewModelExtras].
 *
 * The returned [HiltViewModelExtras] preserves the entry iteration order of the original
 * [HiltViewModelExtras].
 *
 * Those entries of another [hiltViewModelExtras] that are missing in [this] extras are iterated in
 * the end in the order of that [hiltViewModelExtras].
 */
operator fun HiltViewModelExtras.plus(
  hiltViewModelExtras: HiltViewModelExtras
): MutableHiltViewModelExtras =
  MutableHiltViewModelExtras(initialExtras = extras + hiltViewModelExtras.extras)

/**
 * Appends or replaces all entries from the given [hiltViewModelExtras] in [this] mutable extras.
 */
operator fun MutableHiltViewModelExtras.plusAssign(hiltViewModelExtras: HiltViewModelExtras) {
  extras += hiltViewModelExtras.extras
}
