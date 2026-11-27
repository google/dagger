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

package dagger.functional.pruning

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoSet
import dagger.producers.Production
import dagger.producers.ProductionComponent
import dagger.pruning.BindingIdentifier
import dagger.pruning.OnlyUsedBy
import java.util.concurrent.Executor
import javax.inject.Inject

class Foo @Inject constructor() {}

class Bar

class Baz

class Dep1 @Inject constructor() {}

class Dep2 @Inject constructor() {}

class BazAccessor @Inject constructor() {
  fun getBaz(component: PrunableComponent): Baz = component.getBaz()
}

class DepAccessor

class Dep @Inject constructor() {}

class DepAccessorContainer @BindingIdentifier(DepAccessor::class) @Inject constructor() {}

class FooHolder @Inject constructor(val foo: Foo) {}

class BazHolder @Inject constructor(val baz: Baz) {}

class UnusedDep @Inject constructor() {}

class UnusedDep1 @Inject constructor() {}

class UnusedDepHolder @Inject constructor(val unusedDep: UnusedDep) {}

class UsedByTwoBindings @Inject constructor() {}

@Module
class BarModule {
  @Provides fun provideBar(): Bar = Bar()
}

class SameType @Inject constructor() {}

class SameComplexType @Inject constructor(holder: FooHolder) {}

interface SameEntryPoint1 {
  @OnlyUsedBy(UnusedDep::class) fun getSameTypeUsed1(): SameType

  @OnlyUsedBy(Foo::class) fun getSameTypeUsed2(): SameType

  @OnlyUsedBy(UnusedDep::class) fun getSameTypeUnused(): SameType

  @OnlyUsedBy(Foo::class) fun getSameTypeMaybeUsed(): SameType

  @OnlyUsedBy(UnusedDep::class) fun getSameTypeMaybeUsed2(): SameType

  // Currently, the component method is being selected from entry point methods in two places, once
  // in ComponentImplementation, and once in DirectInstanceBindingRepresentation. If the different
  // entry point methods with same signature are selected, then the component implementation will
  // be generated as delegating to a private method that has the same signature as the component
  // method, thus resulting in stack overflow. This test is to ensure that the component method
  // won't delegate to private method.
  @OnlyUsedBy(UnusedDep::class) fun getSameComplexType(): SameComplexType
}

interface SameEntryPoint2 {
  fun getSameTypeUsed1(): SameType

  fun getSameTypeUsed2(): SameType

  @OnlyUsedBy(UnusedDep1::class) fun getSameTypeUnused(): SameType

  @OnlyUsedBy(UnusedDep::class) fun getSameTypeMaybeUsed(): SameType

  @OnlyUsedBy(Foo::class) fun getSameTypeMaybeUsed2(): SameType

  fun getSameComplexType(): SameComplexType
}

@Component(modules = [BarModule::class, ParentComponentModule::class])
interface PrunableComponent : SameEntryPoint1, SameEntryPoint2 {
  // A binding that isn't prunable.
  fun getFoo(): Foo

  // -------------------------------------------------
  // Test generating concrete implementation if entry point is being idetified as in use.
  @OnlyUsedBy(Foo::class) fun getBar(): Bar

  // -------------------------------------------------

  // -------------------------------------------------
  // Test missing binding Baz doesn't fail compilation.
  @OnlyUsedBy(BazAccessor::class) fun getBaz(): Baz

  // -------------------------------------------------

  // -------------------------------------------------
  // Test for two binding annotating each other as entry point accessor, both gets pruned.
  @OnlyUsedBy(Dep1::class) fun getDep2(): Dep2

  @OnlyUsedBy(Dep2::class) fun getDep1(): Dep1

  // -------------------------------------------------

  // -------------------------------------------------
  // Tests successful generation when a binding with an identifier is actually used
  fun getDepAccessorContainer(): DepAccessorContainer

  @OnlyUsedBy(DepAccessor::class) fun getDep(): Dep

  // -------------------------------------------------

  // -------------------------------------------------
  // Test for Binding Accessor is a dependency.
  @OnlyUsedBy(Foo::class) fun getFooHolder(): FooHolder

  @OnlyUsedBy(Baz::class) fun getBazHolder(): BazHolder

  @OnlyUsedBy(UnusedDep::class) fun getUnusedDepHolder(): UnusedDepHolder

  // -------------------------------------------------

  // -------------------------------------------------
  // Test for entry point used from two bindings.
  @OnlyUsedBy(value = [Foo::class, UnusedDep::class]) fun getUsedByTwoBindings(): UsedByTwoBindings

  // -------------------------------------------------

  // -------------------------------------------------
  // Test for entry point referenced from a multibinding in subcomponent that is unused.
  fun getMultiDep(): MultiDep

  @OnlyUsedBy(Child::class) fun usedByMultiBindingInChildComponent(): Foo

  @OnlyUsedBy(Parent::class) fun usedByMultiBindingInThisComponent(): Foo

  fun subComponentFactory(): PrunableSubcomponent.Factory

  // -------------------------------------------------

  // -------------------------------------------------
  // Test for member injection being pruned
  @OnlyUsedBy(UnusedDep::class) fun injectBar(bar: Bar)
}

@Subcomponent(modules = [ChildComponentModule::class])
interface PrunableSubcomponent {
  @OnlyUsedBy(UnusedDep::class) fun getFoo(): Foo

  @OnlyUsedBy(Foo::class) fun getBar(): Bar

  @Subcomponent.Factory
  interface Factory {
    fun create(): PrunableSubcomponent
  }
}

class Child

class Parent

@Module
object ChildComponentModule {
  @IntoSet @Provides @BindingIdentifier(Child::class) fun provideName(): String = "Child"
}

@Module
object ParentComponentModule {
  @IntoSet @Provides @BindingIdentifier(Parent::class) fun provideName(): String = "Parent"
}

class MultiDep @Inject constructor(val dep: Set<String>) {}

// Test producer
@Module
class ExecutorModule {
  @Provides @Production fun executor(): Executor = MoreExecutors.directExecutor()
}

@ProductionComponent(modules = [ExecutorModule::class])
interface PrunableProducerComponent {
  @OnlyUsedBy(UnusedDep::class) fun getBar(): ListenableFuture<Bar>
}
