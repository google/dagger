/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.functional.producers.kotlin

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.producers.ProducerModule
import dagger.producers.Produces
import dagger.producers.Production
import dagger.producers.ProductionComponent
import java.util.concurrent.Executor
import javax.inject.Named

const val OBJECT_MODULE_VALUE = "object_module"
const val OBJECT_MODULE_STATIC_VALUE = "object_module_static"
const val OBJECT_MODULE_SET_VALUE = "object_module_set"
const val NESTED_OBJECT_MODULE_VALUE = "nested_object_module"
const val INTERFACE_COMPANION_OBJECT_MODULE_VALUE = "interface_companion_object_module"
const val NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE =
  "nested_interface_companion_object_module"
const val CLASS_COMPANION_OBJECT_MODULE_VALUE = "class_companion_object_module"

// Regression test for b/347108703
@ProductionComponent(
  modules =
    [
      ExecutorModule::class,
      TestKotlinObjectModule::class,
      TestModuleForNesting.TestNestedKotlinObjectModule::class,
      TestInterfaceCompanionObjectModule::class,
      TestModuleForNesting.TestNestedInterfaceCompanionObjectModule::class,
      TestClassCompanionObjectModule::class,
    ]
)
interface TestKotlinComponentWithObjectModule {
  @Named(OBJECT_MODULE_VALUE) fun getObjectModuleData(): ListenableFuture<TestData>

  @Named(OBJECT_MODULE_STATIC_VALUE) fun getObjectModuleStaticData(): ListenableFuture<TestData>

  @Named(NESTED_OBJECT_MODULE_VALUE) fun getNestedObjectModuleData(): ListenableFuture<TestData>

  @Named(INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
  fun getInterfaceCompanionObjectModuleData(): ListenableFuture<TestData>

  @Named(NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
  fun getNestedInterfaceCompanionObjectModuleData(): ListenableFuture<TestData>

  @Named(CLASS_COMPANION_OBJECT_MODULE_VALUE)
  fun getClassCompanionObjectModuleData(): ListenableFuture<TestData>

  @Named(OBJECT_MODULE_SET_VALUE) fun getSetOfData(): ListenableFuture<Set<TestData>>
}

@ProducerModule
object TestKotlinObjectModule {
  @Produces @Named(OBJECT_MODULE_VALUE) fun provideData() = TestData(OBJECT_MODULE_VALUE)

  @Produces
  @JvmStatic
  @Named(OBJECT_MODULE_STATIC_VALUE)
  fun provideStaticData() = TestData(OBJECT_MODULE_STATIC_VALUE)

  @Produces
  @IntoSet
  @Named(OBJECT_MODULE_SET_VALUE)
  fun provideSetData() = TestData(OBJECT_MODULE_SET_VALUE)
}

class TestModuleForNesting {
  @ProducerModule
  object TestNestedKotlinObjectModule {
    @Produces
    @Named(NESTED_OBJECT_MODULE_VALUE)
    fun provideData() = TestData(NESTED_OBJECT_MODULE_VALUE)
  }

  @ProducerModule
  interface TestNestedInterfaceCompanionObjectModule {
    companion object {
      @Produces
      @Named(NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
      fun provideData() = TestData(NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
    }
  }
}

@ProducerModule
interface TestInterfaceCompanionObjectModule {
  companion object {
    @Produces
    @Named(INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
    fun provideData() = TestData(INTERFACE_COMPANION_OBJECT_MODULE_VALUE)
  }
}

@Suppress("ClassShouldBeObject")
@ProducerModule
class TestClassCompanionObjectModule {
  companion object {
    @Produces
    @Named(CLASS_COMPANION_OBJECT_MODULE_VALUE)
    fun provideData() = TestData(CLASS_COMPANION_OBJECT_MODULE_VALUE)
  }
}

data class TestData(val data: String)

@Module
object ExecutorModule {
  @Provides @Production fun executor(): Executor = MoreExecutors.directExecutor()
}
