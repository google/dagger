/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.builder

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Scope
import javax.inject.Singleton
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BuilderTest {
  @Subcomponent(
    modules =
      [
        StringModule::class,
        IntModuleIncludingDoubleAndFloat::class,
        LongModule::class,
        ByteModule::class
      ]
  )
  internal interface TestChildComponentWithBuilderAbstractClass {
    fun s(): String
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun d(): Double
    fun b(): Byte
    abstract class SharedBuilder<B, C, M1, M2> {
      abstract fun build(): C // Test resolving return type of build()
      abstract fun setM1(m1: M1): B // Test resolving return type & param of setter
      abstract fun setM2(m2: M2): SharedBuilder<B, C, M1, M2> // Test being overridden
      abstract fun setM3(doubleModule: DoubleModule) // Test being overridden
      abstract fun set(
        floatModule: FloatModule
      ): SharedBuilder<B, C, M1, M2> // Test returning supertype.
    }

    @Subcomponent.Builder
    abstract class Builder :
      TestChildComponentWithBuilderAbstractClass.SharedBuilder<
        Builder,
        TestChildComponentWithBuilderAbstractClass,
        StringModule,
        IntModuleIncludingDoubleAndFloat
      >() {
      abstract override fun setM2(m2: IntModuleIncludingDoubleAndFloat): Builder // Test covariance
      abstract override fun setM3(doubleModule: DoubleModule) // Test simple overrides allowed
      abstract fun set(byteModule: ByteModule) // Note we're missing LongModule -- it's implicit
    }
  }

  @Subcomponent(
    modules =
      [
        StringModule::class,
        IntModuleIncludingDoubleAndFloat::class,
        LongModule::class,
        ByteModule::class
      ]
  )
  internal interface TestChildComponentWithBuilderInterface {
    fun s(): String
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun d(): Double
    fun b(): Byte
    interface SharedBuilder<B, C, M1, M2> {
      fun build(): C // Test resolving return type of build()
      fun setM1(m1: M1): B // Test resolving return type & param of setter
      fun setM2(m2: M2): SharedBuilder<B, C, M1, M2> // Test being overridden
      fun setM3(doubleModule: DoubleModule) // Test being overridden
      fun set(
        floatModule: FloatModule
      ): SharedBuilder<B, C, M1, M2> // Test return type is supertype.
    }

    @Subcomponent.Builder
    interface Builder :
      TestChildComponentWithBuilderInterface.SharedBuilder<
        Builder,
        TestChildComponentWithBuilderInterface,
        StringModule,
        IntModuleIncludingDoubleAndFloat
      > {
      override fun setM2(m2: IntModuleIncludingDoubleAndFloat): Builder // Test covariant overrides
      override fun setM3(doubleModule: DoubleModule) // Test simple overrides allowed
      fun set(byteModule: ByteModule) // Note we're missing LongModule -- it's implicit
    }
  }

  @Component(
    modules = [StringModule::class, IntModuleIncludingDoubleAndFloat::class, LongModule::class],
    dependencies = [DepComponent::class]
  )
  internal abstract class TestComponentWithBuilderAbstractClass {
    abstract fun s(): String
    abstract fun i(): Int
    abstract fun l(): Long
    abstract fun f(): Float
    abstract fun d(): Double
    internal abstract class SharedBuilder {
      // Make sure we use the overriding signature.
      abstract fun build(): Any
      open fun stringModule(stringModule: StringModule): Any? = null
      @Suppress("UNUSED_PARAMETER")
      fun ignoredLongModule(longModule: LongModule): SharedBuilder? = null
    }

    @Component.Builder
    internal abstract class Builder : TestComponentWithBuilderAbstractClass.SharedBuilder() {
      abstract override fun build(): TestComponentWithBuilderAbstractClass // Narrowing return type
      abstract override fun stringModule(stringModule: StringModule): Builder // Abstract & narrow
      abstract fun intModule(intModule: IntModuleIncludingDoubleAndFloat): Builder
      abstract fun doubleModule(doubleModule: DoubleModule) // Module w/o args
      abstract fun depComponent(depComponent: DepComponent)
      // Note we're missing LongModule & FloatModule -- they/re implicit
      @Suppress("UNUSED_PARAMETER")
      fun ignoredIntModule(intModule: IntModuleIncludingDoubleAndFloat): Builder? = null
    }

    companion object {
      fun builder(): Builder = DaggerBuilderTest_TestComponentWithBuilderAbstractClass.builder()
    }
  }

  @Component(
    modules = [StringModule::class, IntModuleIncludingDoubleAndFloat::class, LongModule::class],
    dependencies = [DepComponent::class]
  )
  internal interface TestComponentWithBuilderInterface {
    fun s(): String
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun d(): Double
    interface SharedBuilder {
      // Make sure we use the overriding signature.
      fun build(): Any
      fun stringModule(m1: StringModule): Any
    }

    @Component.Builder
    interface Builder : TestComponentWithBuilderInterface.SharedBuilder {
      override fun build(): TestComponentWithBuilderInterface // Narrowing return type
      override fun stringModule(m1: StringModule): Builder // Narrowing return type
      fun intModule(intModule: IntModuleIncludingDoubleAndFloat): Builder
      fun doubleModule(doubleModule: DoubleModule) // Module w/o args
      fun depComponent(
        depComponent: DepComponent
      ) // Note we're missing LongModule & FloatModule -- they/re implicit
    }
  }

  @Component(
    modules = [StringModule::class, IntModuleIncludingDoubleAndFloat::class, LongModule::class],
    dependencies = [DepComponent::class]
  )
  internal interface TestComponentWithGenericBuilderAbstractClass {
    fun s(): String
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun d(): Double
    abstract class SharedBuilder<B, C, M1, M2> {
      abstract fun build(): C // Test resolving return type of build()
      abstract fun setM1(m1: M1): B // Test resolving return type & param of setter
      abstract fun setM2(m2: M2): SharedBuilder<B, C, M1, M2> // Test being overridden
      abstract fun doubleModule(doubleModule: DoubleModule) // Test being overridden
      abstract fun depComponent(
        floatModule: FloatModule
      ): SharedBuilder<B, C, M1, M2> // Test return type
    }

    @Component.Builder
    abstract class Builder :
      TestComponentWithGenericBuilderAbstractClass.SharedBuilder<
        Builder,
        TestComponentWithGenericBuilderAbstractClass,
        StringModule,
        IntModuleIncludingDoubleAndFloat
      >() {
      abstract override fun setM2(
        m2: IntModuleIncludingDoubleAndFloat
      ): Builder // Test covariant overrides
      abstract override fun doubleModule(
        doubleModule: DoubleModule
      ) // Test simple overrides allowed
      abstract fun depComponent(
        depComponent: DepComponent
      ) // Note we're missing LongModule & FloatModule -- they're implicit
    }
  }

  @Component(
    modules = [StringModule::class, IntModuleIncludingDoubleAndFloat::class, LongModule::class],
    dependencies = [DepComponent::class]
  )
  internal interface TestComponentWithGenericBuilderInterface {
    fun s(): String
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun d(): Double
    interface SharedBuilder<B, C, M1, M2> {
      fun build(): C // Test resolving return type of build()
      fun setM1(m1: M1): B // Test resolving return type & param of setter
      fun setM2(m2: M2): SharedBuilder<B, C, M1, M2> // Test being overridden
      fun doubleModule(doubleModule: DoubleModule) // Test being overridden
      fun set(
        floatModule: FloatModule
      ): SharedBuilder<B, C, M1, M2> // Test return type is supertype.
    }

    @Component.Builder
    interface Builder :
      TestComponentWithGenericBuilderInterface.SharedBuilder<
        Builder,
        TestComponentWithGenericBuilderInterface,
        StringModule,
        IntModuleIncludingDoubleAndFloat
      > {
      override fun setM2(
        m2: IntModuleIncludingDoubleAndFloat
      ): Builder // Test covariant overrides allowed
      override fun doubleModule(doubleModule: DoubleModule) // Test simple overrides allowed
      fun depComponent(depComponent: DepComponent) // Note we're missing M5 -- that's implicit.
    }
  }

  @Component internal interface DepComponent

  @Singleton
  @Component
  internal interface ParentComponent {
    fun childAbstractClassBuilder(): TestChildComponentWithBuilderAbstractClass.Builder
    fun childInterfaceBuilder(): TestChildComponentWithBuilderInterface.Builder
    fun middleBuilder(): MiddleChild.Builder
    fun otherBuilder(): OtherMiddleChild.Builder
    fun requiresMiddleChildBuilder(): RequiresSubcomponentBuilder<MiddleChild.Builder>
  }

  @Scope internal annotation class MiddleScope

  @MiddleScope
  @Subcomponent(modules = [StringModule::class])
  internal interface MiddleChild {
    fun s(): String
    fun grandchildBuilder(): Grandchild.Builder
    fun requiresGrandchildBuilder(): RequiresSubcomponentBuilder<Grandchild.Builder>

    @Subcomponent.Builder
    interface Builder {
      fun build(): MiddleChild
      fun set(stringModule: StringModule): Builder
    }
  }

  internal class RequiresSubcomponentBuilder<B>
  @Inject
  constructor(
    private val subcomponentBuilderProvider: Provider<B>,
    private val subcomponentBuilder: B
  ) {
    fun subcomponentBuilderProvider() = subcomponentBuilderProvider
    fun subcomponentBuilder() = subcomponentBuilder
  }

  @MiddleScope
  @Subcomponent(modules = [StringModule::class, LongModule::class])
  internal interface OtherMiddleChild {
    fun l(): Long
    fun s(): String
    fun grandchildBuilder(): Grandchild.Builder

    @Subcomponent.Builder
    interface Builder {
      fun build(): OtherMiddleChild
      fun set(stringModule: StringModule): Builder
    }
  }

  @Component(modules = [StringModule::class])
  @Singleton
  internal interface ParentOfGenericComponent : GenericParent<Grandchild.Builder>

  @Subcomponent(modules = [IntModuleIncludingDoubleAndFloat::class])
  internal interface Grandchild {
    fun i(): Int
    fun s(): String

    @Subcomponent.Builder
    interface Builder {
      fun build(): Grandchild
      fun set(intModule: IntModuleIncludingDoubleAndFloat): Builder
    }
  }

  internal interface GenericParent<B> {
    fun subcomponentBuilder(): B
  }

  @Module
  internal class ByteModule(private val b: Byte) {
    @Provides fun b(): Byte = b
  }

  @Module
  internal class DoubleModule {
    @Provides fun d(): Double = 4.2
  }

  @Module
  internal class LongModule {
    @Provides fun l(): Long = 6L
  }

  @Module
  internal class FloatModule {
    @Provides fun f(): Float = 5.5f
  }

  @Module
  internal class StringModule(private val string: String) {
    @Provides fun string(): String = string
  }

  @Module(includes = [DoubleModule::class, FloatModule::class])
  internal class IntModuleIncludingDoubleAndFloat(private val integer: Int) {
    @Provides fun integer(): Int = integer
  }

  @Test
  fun interfaceBuilder() {
    val builder = DaggerBuilderTest_TestComponentWithBuilderInterface.builder()

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder
      .intModule(IntModuleIncludingDoubleAndFloat(1))
      .stringModule(StringModule("sam"))
      .depComponent(object : DepComponent {})
    builder.doubleModule(DoubleModule())

    // Don't set other modules -- make sure it works.
    val component = builder.build()
    assertThat(component.s()).isEqualTo("sam")
    assertThat(component.i()).isEqualTo(1)
    assertThat(component.d()).isEqualTo(4.2)
    assertThat(component.f()).isEqualTo(5.5f)
    assertThat(component.l()).isEqualTo(6L)
  }

  @Test
  fun abstractClassBuilder() {
    val builder = TestComponentWithBuilderAbstractClass.builder()

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder
      .intModule(IntModuleIncludingDoubleAndFloat(1))
      .stringModule(StringModule("sam"))
      .depComponent(object : DepComponent {})
    builder.doubleModule(DoubleModule())

    // Don't set other modules -- make sure it works.
    val component = builder.build()
    assertThat(component.s()).isEqualTo("sam")
    assertThat(component.i()).isEqualTo(1)
    assertThat(component.d()).isEqualTo(4.2)
    assertThat(component.f()).isEqualTo(5.5f)
    assertThat(component.l()).isEqualTo(6L)
  }

  @Test
  fun interfaceGenericBuilder() {
    val builder = DaggerBuilderTest_TestComponentWithGenericBuilderInterface.builder()

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder
      .setM2(IntModuleIncludingDoubleAndFloat(1))
      .setM1(StringModule("sam"))
      .depComponent(object : DepComponent {})
    builder.doubleModule(DoubleModule())

    // Don't set other modules -- make sure it works.
    val component = builder.build()
    assertThat(component.s()).isEqualTo("sam")
    assertThat(component.i()).isEqualTo(1)
    assertThat(component.d()).isEqualTo(4.2)
    assertThat(component.f()).isEqualTo(5.5f)
    assertThat(component.l()).isEqualTo(6L)
  }

  @Test
  fun abstractClassGenericBuilder() {
    val builder = DaggerBuilderTest_TestComponentWithGenericBuilderAbstractClass.builder()

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder
      .setM2(IntModuleIncludingDoubleAndFloat(1))
      .setM1(StringModule("sam"))
      .depComponent(object : DepComponent {})
    builder.doubleModule(DoubleModule())

    // Don't set other modules -- make sure it works.
    val component = builder.build()
    assertThat(component.s()).isEqualTo("sam")
    assertThat(component.i()).isEqualTo(1)
    assertThat(component.d()).isEqualTo(4.2)
    assertThat(component.f()).isEqualTo(5.5f)
    assertThat(component.l()).isEqualTo(6L)
  }

  @Test
  fun subcomponents_interface() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val builder1 = parent.childInterfaceBuilder()

    try {
      builder1.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder1
      .setM2(IntModuleIncludingDoubleAndFloat(1))
      .setM1(StringModule("sam"))
      .set(ByteModule(7.toByte()))
    builder1.set(FloatModule())

    val child1 = builder1.build()
    assertThat(child1.s()).isEqualTo("sam")
    assertThat(child1.i()).isEqualTo(1)
    assertThat(child1.d()).isEqualTo(4.2)
    assertThat(child1.f()).isEqualTo(5.5f)
    assertThat(child1.l()).isEqualTo(6L)
    assertThat(child1.b()).isEqualTo(7.toByte())
  }

  @Test
  fun subcomponents_abstractclass() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val builder2 = parent.childAbstractClassBuilder()

    try {
      builder2.build()
      fail()
    } catch (expected: IllegalStateException) {}

    builder2
      .setM2(IntModuleIncludingDoubleAndFloat(10))
      .setM1(StringModule("tara"))
      .set(ByteModule(70.toByte()))
    builder2.set(FloatModule())

    val child2 = builder2.build()
    assertThat(child2.s()).isEqualTo("tara")
    assertThat(child2.i()).isEqualTo(10)
    assertThat(child2.d()).isEqualTo(4.2)
    assertThat(child2.f()).isEqualTo(5.5f)
    assertThat(child2.l()).isEqualTo(6L)
    assertThat(child2.b()).isEqualTo(70.toByte())
  }

  @Test
  fun grandchildren() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val middle1 = parent.middleBuilder().set(StringModule("sam")).build()
    val grandchild1 = middle1.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(21)).build()
    val grandchild2 = middle1.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(22)).build()
    assertThat(middle1.s()).isEqualTo("sam")
    assertThat(grandchild1.i()).isEqualTo(21)
    assertThat(grandchild1.s()).isEqualTo("sam")
    assertThat(grandchild2.i()).isEqualTo(22)
    assertThat(grandchild2.s()).isEqualTo("sam")

    // Make sure grandchildren from newer children have no relation to the older ones.
    val middle2 = parent.middleBuilder().set(StringModule("tara")).build()
    val grandchild3 = middle2.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(23)).build()
    val grandchild4 = middle2.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(24)).build()
    assertThat(middle2.s()).isEqualTo("tara")
    assertThat(grandchild3.i()).isEqualTo(23)
    assertThat(grandchild3.s()).isEqualTo("tara")
    assertThat(grandchild4.i()).isEqualTo(24)
    assertThat(grandchild4.s()).isEqualTo("tara")
  }

  @Test
  fun diamondGrandchildren() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val middle = parent.middleBuilder().set(StringModule("sam")).build()
    val other = parent.otherBuilder().set(StringModule("tara")).build()
    val middlegrand = middle.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(21)).build()
    val othergrand = other.grandchildBuilder().set(IntModuleIncludingDoubleAndFloat(22)).build()
    assertThat(middle.s()).isEqualTo("sam")
    assertThat(other.s()).isEqualTo("tara")
    assertThat(middlegrand.s()).isEqualTo("sam")
    assertThat(othergrand.s()).isEqualTo("tara")
    assertThat(middlegrand.i()).isEqualTo(21)
    assertThat(othergrand.i()).isEqualTo(22)
  }

  @Test
  fun genericSubcomponentMethod() {
    val parent =
      DaggerBuilderTest_ParentOfGenericComponent.builder().stringModule(StringModule("sam")).build()
    val builder = parent.subcomponentBuilder()
    val child = builder.set(IntModuleIncludingDoubleAndFloat(21)).build()
    assertThat(child.s()).isEqualTo("sam")
    assertThat(child.i()).isEqualTo(21)
  }

  @Test
  fun requireSubcomponentBuilderProviders() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val middle =
      parent
        .requiresMiddleChildBuilder()
        .subcomponentBuilderProvider()
        .get()
        .set(StringModule("sam"))
        .build()
    val grandchild =
      middle
        .requiresGrandchildBuilder()
        .subcomponentBuilderProvider()
        .get()
        .set(IntModuleIncludingDoubleAndFloat(12))
        .build()
    assertThat(middle.s()).isEqualTo("sam")
    assertThat(grandchild.i()).isEqualTo(12)
    assertThat(grandchild.s()).isEqualTo("sam")
  }

  @Test
  fun requireSubcomponentBuilders() {
    val parent = DaggerBuilderTest_ParentComponent.create()
    val middle =
      parent.requiresMiddleChildBuilder().subcomponentBuilder().set(StringModule("sam")).build()
    val grandchild =
      middle
        .requiresGrandchildBuilder()
        .subcomponentBuilder()
        .set(IntModuleIncludingDoubleAndFloat(12))
        .build()
    assertThat(middle.s()).isEqualTo("sam")
    assertThat(grandchild.i()).isEqualTo(12)
    assertThat(grandchild.s()).isEqualTo("sam")
  }
}
