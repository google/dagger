/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.plugin.util

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Variant
import org.gradle.api.Project

/**
 * Compatibility version of [com.android.build.api.variant.AndroidComponentsExtension]
 * - In AGP 4.2 its package is 'com.android.build.api.extension'
 * - In AGP 7.0 its packages is 'com.android.build.api.variant'
 */
sealed class AndroidComponentsExtensionCompat {

  /**
   * A combined compatibility function of
   * [com.android.build.api.variant.AndroidComponentsExtension.onVariants] that includes also
   * [AndroidTest] and [UnitTest] variants.
   */
  abstract fun onAllVariants(block: (ComponentCompat) -> Unit)

  class Api70Impl(
    private val actual: AndroidComponentsExtension<*, *, *>
  ) : AndroidComponentsExtensionCompat() {
    override fun onAllVariants(block: (ComponentCompat) -> Unit) {
      actual.onVariants { variant ->
        // Use reflection to get the AndroidTest component out of the variant because a binary
        // incompatible change was introduced in AGP 7.0-beta05 that changed the return type of the
        // method.
        fun ApplicationVariant.getAndroidTest() =
          this::class.java.getDeclaredMethod("getAndroidTest").invoke(this) as? Component
        fun LibraryVariant.getAndroidTest() =
          this::class.java.getDeclaredMethod("getAndroidTest").invoke(this) as? Component
        block.invoke(ComponentCompat.Api70Impl(variant))
        when (variant) {
          is ApplicationVariant -> variant.getAndroidTest()
          is LibraryVariant -> variant.getAndroidTest()
          else -> null
        }?.let { block.invoke(ComponentCompat.Api70Impl(it)) }
        // Use reflection too to get the UnitTest component since in 7.2
        // com.android.build.api.component.UnitTest was removed and replaced by
        // com.android.build.api.variant.UnitTest causing the return type of Variant#getUnitTest()
        // to change and break ABI.
        fun Variant.getUnitTest() =
          this::class.java.getDeclaredMethod("getUnitTest").invoke(this) as? Component
        variant.getUnitTest()?.let { block.invoke(ComponentCompat.Api70Impl(it)) }
      }
    }
  }

  class Api42Impl(private val actual: Any) : AndroidComponentsExtensionCompat() {

    private val extensionClazz =
      Class.forName("com.android.build.api.extension.AndroidComponentsExtension")

    private val variantSelectorClazz =
      Class.forName("com.android.build.api.extension.VariantSelector")

    override fun onAllVariants(block: (ComponentCompat) -> Unit) {
      val selector = extensionClazz.getDeclaredMethod("selector").invoke(actual)
      val allSelector = variantSelectorClazz.getDeclaredMethod("all").invoke(selector)
      val wrapFunction: (Any) -> Unit = {
        block.invoke(ComponentCompat.Api42Impl(it))
      }
      listOf("onVariants", "androidTests", "unitTests").forEach { methodName ->
        extensionClazz.getDeclaredMethod(
          methodName, variantSelectorClazz, Function1::class.java
        ).invoke(actual, allSelector, wrapFunction)
      }
    }
  }

  companion object {
    fun getAndroidComponentsExtension(project: Project): AndroidComponentsExtensionCompat {
      return if (
        findClass("com.android.build.api.variant.AndroidComponentsExtension") != null
      ) {
        val actualExtension = project.extensions.getByType(AndroidComponentsExtension::class.java)
        Api70Impl(actualExtension)
      } else {
        val actualExtension = project.extensions.getByType(
          Class.forName("com.android.build.api.extension.AndroidComponentsExtension")
        )
        Api42Impl(actualExtension)
      }
    }
  }
}

/**
 * Compatibility version of [com.android.build.api.variant.Component]
 * - In AGP 4.2 its package is 'com.android.build.api.component'
 * - In AGP 7.0 its packages is 'com.android.build.api.variant'
 */
@Suppress("UnstableApiUsage") // ASM Pipeline APIs
sealed class ComponentCompat {

  /**
   * Redeclaration of [com.android.build.api.variant.ComponentIdentity.name]
   */
  abstract val name: String

  /**
   * Redeclaration of [com.android.build.api.variant.Component.transformClassesWith]
   */
  abstract fun <ParamT : InstrumentationParameters> transformClassesWith(
    classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
    scope: InstrumentationScope,
    instrumentationParamsConfig: (ParamT) -> Unit
  )

  /**
   * Redeclaration of [com.android.build.api.variant.Component.setAsmFramesComputationMode]
   */
  abstract fun setAsmFramesComputationMode(mode: FramesComputationMode)

  class Api70Impl(private val component: Component) : ComponentCompat() {

    override val name: String
      get() = component.name

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
      classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
      scope: InstrumentationScope,
      instrumentationParamsConfig: (ParamT) -> Unit
    ) {
      component.transformClassesWith(
        classVisitorFactoryImplClass, scope, instrumentationParamsConfig
      )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
      component.setAsmFramesComputationMode(mode)
    }
  }

  class Api42Impl(private val actual: Any) : ComponentCompat() {

    private val componentClazz = Class.forName("com.android.build.api.component.Component")

    override val name: String
      get() = componentClazz.getMethod("getName").invoke(actual) as String

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
      classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
      scope: InstrumentationScope,
      instrumentationParamsConfig: (ParamT) -> Unit
    ) {
      componentClazz.getDeclaredMethod(
        "transformClassesWith",
        Class::class.java, InstrumentationScope::class.java, Function1::class.java
      ).invoke(actual, classVisitorFactoryImplClass, scope, instrumentationParamsConfig)
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
      componentClazz.getDeclaredMethod(
        "setAsmFramesComputationMode", FramesComputationMode::class.java
      ).invoke(actual, mode)
    }
  }
}

fun findClass(fqName: String) = try {
  Class.forName(fqName)
} catch (ex: ClassNotFoundException) {
  null
}
