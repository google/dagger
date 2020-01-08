package dagger.functional.kotlin

import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Component(
  modules = [
    TestKotlinModuleWithCompanion::class,
    TestKotlinModuleWithNamedCompanion::class,
    TestKotlinAbstractModuleWithCompanion::class,
    TestKotlinWorkaroundModuleWithCompanion::class
  ]
)
interface TestKotlinComponentWithCompanionModule {
  fun getDataA(): TestDataA
  fun getDataB(): TestDataB
  fun getBoolean(): Boolean
  fun getStringType(): String
  @Named("Cat")
  fun getNamedStringType(): String

  fun getInterface(): TestInterface
  fun getLong(): Long
  fun getDouble(): Double
  fun getInteger(): Int
}

@Module
class TestKotlinModuleWithCompanion {
  @Provides
  fun provideDataA() = TestDataA("test")

  companion object {
    @Provides
    fun provideDataB() = TestDataB("test")

    @Provides
    fun provideBoolean(): Boolean = true
  }
}

@Module
class TestKotlinModuleWithNamedCompanion {

  @Provides
  @Named("Cat")
  fun provideNamedString() = "Cat"

  companion object Foo {
    @Provides
    fun provideStringType(): String = ""
  }
}

@Module
abstract class TestKotlinAbstractModuleWithCompanion {

  @Binds
  abstract fun bindInterface(injectable: TestInjectable): TestInterface

  companion object {
    @Provides
    fun provideLong() = 4L
  }
}

@Module
class TestKotlinWorkaroundModuleWithCompanion {

  @Provides
  fun provideDouble() = 1.0

  @Module
  companion object {
    @Provides
    @JvmStatic
    fun provideInteger() = 2
  }
}
