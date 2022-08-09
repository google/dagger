/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.builder;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.fail;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuilderTest {
  @Subcomponent(
      modules = {
        StringModule.class,
        IntModuleIncludingDoubleAndFloat.class,
        LongModule.class,
        ByteModule.class
      })
  interface TestChildComponentWithBuilderAbstractClass {
    String s();

    int i();

    long l();

    float f();

    double d();

    byte b();

    abstract static class SharedBuilder<B, C, M1, M2> {
      abstract C build(); // Test resolving return type of build()

      abstract B setM1(M1 m1); // Test resolving return type & param of setter

      abstract SharedBuilder<B, C, M1, M2> setM2(M2 m2); // Test being overridden

      abstract void setM3(DoubleModule doubleModule); // Test being overridden

      abstract SharedBuilder<B, C, M1, M2> set(
          FloatModule floatModule); // Test returning supertype.
    }

    @Subcomponent.Builder
    abstract static class Builder
        extends TestChildComponentWithBuilderAbstractClass.SharedBuilder<
            Builder,
            TestChildComponentWithBuilderAbstractClass,
            StringModule,
            IntModuleIncludingDoubleAndFloat> {
      @Override
      abstract Builder setM2(IntModuleIncludingDoubleAndFloat m2); // Test covariance

      @Override
      abstract void setM3(DoubleModule doubleModule); // Test simple overrides allowed

      abstract void set(ByteModule byteModule);

      // Note we're missing LongModule -- it's implicit
    }
  }

  @Subcomponent(
      modules = {
        StringModule.class,
        IntModuleIncludingDoubleAndFloat.class,
        LongModule.class,
        ByteModule.class
      })
  interface TestChildComponentWithBuilderInterface {
    String s();

    int i();

    long l();

    float f();

    double d();

    byte b();

    interface SharedBuilder<B, C, M1, M2> {
      C build(); // Test resolving return type of build()

      B setM1(M1 m1); // Test resolving return type & param of setter

      SharedBuilder<B, C, M1, M2> setM2(M2 m2); // Test being overridden

      void setM3(DoubleModule doubleModule); // Test being overridden

      SharedBuilder<B, C, M1, M2> set(FloatModule floatModule); // Test return type is supertype.
    }

    @Subcomponent.Builder
    interface Builder
        extends TestChildComponentWithBuilderInterface.SharedBuilder<
            Builder,
            TestChildComponentWithBuilderInterface,
            StringModule,
            IntModuleIncludingDoubleAndFloat> {
      @Override
      Builder setM2(IntModuleIncludingDoubleAndFloat m2); // Test covariant overrides

      @Override
      void setM3(DoubleModule doubleModule); // Test simple overrides allowed

      void set(ByteModule byteModule);

      // Note we're missing LongModule -- it's implicit
    }
  }

  @Component(
      modules = {StringModule.class, IntModuleIncludingDoubleAndFloat.class, LongModule.class},
      dependencies = DepComponent.class)
  abstract static class TestComponentWithBuilderAbstractClass {

    static Builder builder() {
      return DaggerBuilderTest_TestComponentWithBuilderAbstractClass.builder();
    }

    abstract String s();

    abstract int i();

    abstract long l();

    abstract float f();

    abstract double d();

    abstract static class SharedBuilder {
      // Make sure we use the overriding signature.
      abstract Object build();

      Object stringModule(@SuppressWarnings("unused") StringModule stringModule) {
        return null;
      }

      SharedBuilder ignoredLongModule(@SuppressWarnings("unused") LongModule longModule) {
        return null;
      }
    }

    @Component.Builder
    abstract static class Builder extends TestComponentWithBuilderAbstractClass.SharedBuilder {
      @Override
      abstract TestComponentWithBuilderAbstractClass build(); // Narrowing return type

      @Override
      abstract Builder stringModule(StringModule stringModule); // Make abstract & narrow

      abstract Builder intModule(IntModuleIncludingDoubleAndFloat intModule);

      abstract void doubleModule(DoubleModule doubleModule); // Module w/o args

      abstract void depComponent(DepComponent depComponent);

      Builder ignoredIntModule(
          @SuppressWarnings("unused") IntModuleIncludingDoubleAndFloat intModule) {
        return null;
      }

      // Note we're missing LongModule & FloatModule -- they/re implicit
    }
  }

  @Component(
      modules = {StringModule.class, IntModuleIncludingDoubleAndFloat.class, LongModule.class},
      dependencies = DepComponent.class)
  interface TestComponentWithBuilderInterface {
    String s();

    int i();

    long l();

    float f();

    double d();

    interface SharedBuilder {
      // Make sure we use the overriding signature.
      Object build();

      Object stringModule(StringModule m1);
    }

    @Component.Builder
    interface Builder extends TestComponentWithBuilderInterface.SharedBuilder {
      @Override
      TestComponentWithBuilderInterface build(); // Narrowing return type

      @Override
      Builder stringModule(StringModule stringModule); // Narrowing return type

      Builder intModule(IntModuleIncludingDoubleAndFloat intModule);

      void doubleModule(DoubleModule doubleModule); // Module w/o args

      void depComponent(DepComponent depComponent);

      // Note we're missing LongModule & FloatModule -- they/re implicit
    }
  }

  @Component(
      modules = {StringModule.class, IntModuleIncludingDoubleAndFloat.class, LongModule.class},
      dependencies = DepComponent.class)
  interface TestComponentWithGenericBuilderAbstractClass {
    String s();

    int i();

    long l();

    float f();

    double d();

    abstract static class SharedBuilder<B, C, M1, M2> {
      abstract C build(); // Test resolving return type of build()

      abstract B setM1(M1 m1); // Test resolving return type & param of setter

      abstract SharedBuilder<B, C, M1, M2> setM2(M2 m2); // Test being overridden

      abstract void doubleModule(DoubleModule doubleModule); // Test being overridden

      abstract SharedBuilder<B, C, M1, M2> depComponent(
          FloatModule floatModule); // Test return type
    }

    @Component.Builder
    abstract static class Builder
        extends TestComponentWithGenericBuilderAbstractClass.SharedBuilder<
            Builder,
            TestComponentWithGenericBuilderAbstractClass,
            StringModule,
            IntModuleIncludingDoubleAndFloat> {
      @Override
      abstract Builder setM2(IntModuleIncludingDoubleAndFloat m2); // Test covariant overrides

      @Override
      abstract void doubleModule(DoubleModule module3); // Test simple overrides allowed

      abstract void depComponent(DepComponent depComponent);

      // Note we're missing LongModule & FloatModule -- they're implicit
    }
  }

  @Component(
      modules = {StringModule.class, IntModuleIncludingDoubleAndFloat.class, LongModule.class},
      dependencies = DepComponent.class)
  interface TestComponentWithGenericBuilderInterface {
    String s();

    int i();

    long l();

    float f();

    double d();

    interface SharedBuilder<B, C, M1, M2> {
      C build(); // Test resolving return type of build()

      B setM1(M1 m1); // Test resolving return type & param of setter

      SharedBuilder<B, C, M1, M2> setM2(M2 m2); // Test being overridden

      void doubleModule(DoubleModule doubleModule); // Test being overridden

      SharedBuilder<B, C, M1, M2> set(FloatModule floatModule); // Test return type is supertype.
    }

    @Component.Builder
    interface Builder
        extends TestComponentWithGenericBuilderInterface.SharedBuilder<
            Builder,
            TestComponentWithGenericBuilderInterface,
            StringModule,
            IntModuleIncludingDoubleAndFloat> {
      @Override
      Builder setM2(IntModuleIncludingDoubleAndFloat m2); // Test covariant overrides allowed

      @Override
      void doubleModule(DoubleModule module3); // Test simple overrides allowed

      void depComponent(DepComponent depComponent);

      // Note we're missing M5 -- that's implicit.
    }
  }

  @Component
  interface DepComponent {}

  @Singleton
  @Component
  interface ParentComponent {
    TestChildComponentWithBuilderAbstractClass.Builder childAbstractClassBuilder();

    TestChildComponentWithBuilderInterface.Builder childInterfaceBuilder();

    MiddleChild.Builder middleBuilder();

    OtherMiddleChild.Builder otherBuilder();

    RequiresSubcomponentBuilder<MiddleChild.Builder> requiresMiddleChildBuilder();
  }

  @Scope
  @Retention(RUNTIME)
  @interface MiddleScope {}

  @MiddleScope
  @Subcomponent(modules = StringModule.class)
  interface MiddleChild {
    String s();

    Grandchild.Builder grandchildBuilder();

    RequiresSubcomponentBuilder<Grandchild.Builder> requiresGrandchildBuilder();

    @Subcomponent.Builder
    interface Builder {
      MiddleChild build();

      Builder set(StringModule stringModule);
    }
  }

  static class RequiresSubcomponentBuilder<B> {
    private final Provider<B> subcomponentBuilderProvider;
    private final B subcomponentBuilder;

    @Inject
    RequiresSubcomponentBuilder(Provider<B> subcomponentBuilderProvider, B subcomponentBuilder) {
      this.subcomponentBuilderProvider = subcomponentBuilderProvider;
      this.subcomponentBuilder = subcomponentBuilder;
    }

    Provider<B> subcomponentBuilderProvider() {
      return subcomponentBuilderProvider;
    }

    B subcomponentBuilder() {
      return subcomponentBuilder;
    }
  }

  @MiddleScope
  @Subcomponent(modules = {StringModule.class, LongModule.class})
  interface OtherMiddleChild {
    long l();

    String s();

    Grandchild.Builder grandchildBuilder();

    @Subcomponent.Builder
    interface Builder {
      OtherMiddleChild build();

      Builder set(StringModule stringModule);
    }
  }

  @Component(modules = StringModule.class)
  @Singleton
  interface ParentOfGenericComponent extends GenericParent<Grandchild.Builder> {}

  @Subcomponent(modules = IntModuleIncludingDoubleAndFloat.class)
  interface Grandchild {
    int i();

    String s();

    @Subcomponent.Builder
    interface Builder {
      Grandchild build();

      Builder set(IntModuleIncludingDoubleAndFloat intModule);
    }
  }

  interface GenericParent<B> {
    B subcomponentBuilder();
  }

  @Module
  static class ByteModule {
    final byte b;

    ByteModule(byte b) {
      this.b = b;
    }

    @Provides
    byte b() {
      return b;
    }
  }

  @Module
  static class DoubleModule {
    @Provides
    double d() {
      return 4.2d;
    }
  }

  @Module
  static class LongModule {
    @Provides
    long l() {
      return 6L;
    }
  }

  @Module
  static class FloatModule {
    @Provides
    float f() {
      return 5.5f;
    }
  }

  @Module
  static class StringModule {
    final String string;

    StringModule(String string) {
      this.string = string;
    }

    @Provides
    String string() {
      return string;
    }
  }

  @Module(includes = {DoubleModule.class, FloatModule.class})
  static class IntModuleIncludingDoubleAndFloat {
    final int integer;

    IntModuleIncludingDoubleAndFloat(int integer) {
      this.integer = integer;
    }

    @Provides
    int integer() {
      return integer;
    }
  }

  @Test
  public void interfaceBuilder() {
    TestComponentWithBuilderInterface.Builder builder =
        DaggerBuilderTest_TestComponentWithBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder
        .intModule(new IntModuleIncludingDoubleAndFloat(1))
        .stringModule(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.

    TestComponentWithBuilderInterface component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isEqualTo(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test
  public void abstractClassBuilder() {
    TestComponentWithBuilderAbstractClass.Builder builder =
        TestComponentWithBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder
        .intModule(new IntModuleIncludingDoubleAndFloat(1))
        .stringModule(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.

    TestComponentWithBuilderAbstractClass component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isEqualTo(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test
  public void interfaceGenericBuilder() {
    TestComponentWithGenericBuilderInterface.Builder builder =
        DaggerBuilderTest_TestComponentWithGenericBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder
        .setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.

    TestComponentWithGenericBuilderInterface component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isEqualTo(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test
  public void abstractClassGenericBuilder() {
    TestComponentWithGenericBuilderAbstractClass.Builder builder =
        DaggerBuilderTest_TestComponentWithGenericBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder
        .setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.

    TestComponentWithGenericBuilderAbstractClass component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isEqualTo(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test
  public void subcomponents_interface() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    TestChildComponentWithBuilderInterface.Builder builder1 = parent.childInterfaceBuilder();
    try {
      builder1.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder1
        .setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .set(new ByteModule((byte) 7));
    builder1.set(new FloatModule());
    TestChildComponentWithBuilderInterface child1 = builder1.build();
    assertThat(child1.s()).isEqualTo("sam");
    assertThat(child1.i()).isEqualTo(1);
    assertThat(child1.d()).isEqualTo(4.2d);
    assertThat(child1.f()).isEqualTo(5.5f);
    assertThat(child1.l()).isEqualTo(6L);
    assertThat(child1.b()).isEqualTo((byte) 7);
  }

  @Test
  public void subcomponents_abstractclass() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    TestChildComponentWithBuilderAbstractClass.Builder builder2 =
        parent.childAbstractClassBuilder();
    try {
      builder2.build();
      fail();
    } catch (IllegalStateException expected) {
    }

    builder2
        .setM2(new IntModuleIncludingDoubleAndFloat(10))
        .setM1(new StringModule("tara"))
        .set(new ByteModule((byte) 70));
    builder2.set(new FloatModule());
    TestChildComponentWithBuilderAbstractClass child2 = builder2.build();
    assertThat(child2.s()).isEqualTo("tara");
    assertThat(child2.i()).isEqualTo(10);
    assertThat(child2.d()).isEqualTo(4.2d);
    assertThat(child2.f()).isEqualTo(5.5f);
    assertThat(child2.l()).isEqualTo(6L);
    assertThat(child2.b()).isEqualTo((byte) 70);
  }

  @Test
  public void grandchildren() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    MiddleChild middle1 = parent.middleBuilder().set(new StringModule("sam")).build();
    Grandchild grandchild1 =
        middle1.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(21)).build();
    Grandchild grandchild2 =
        middle1.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(22)).build();

    assertThat(middle1.s()).isEqualTo("sam");
    assertThat(grandchild1.i()).isEqualTo(21);
    assertThat(grandchild1.s()).isEqualTo("sam");
    assertThat(grandchild2.i()).isEqualTo(22);
    assertThat(grandchild2.s()).isEqualTo("sam");

    // Make sure grandchildren from newer children have no relation to the older ones.
    MiddleChild middle2 = parent.middleBuilder().set(new StringModule("tara")).build();
    Grandchild grandchild3 =
        middle2.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(23)).build();
    Grandchild grandchild4 =
        middle2.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(24)).build();

    assertThat(middle2.s()).isEqualTo("tara");
    assertThat(grandchild3.i()).isEqualTo(23);
    assertThat(grandchild3.s()).isEqualTo("tara");
    assertThat(grandchild4.i()).isEqualTo(24);
    assertThat(grandchild4.s()).isEqualTo("tara");
  }

  @Test
  public void diamondGrandchildren() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    MiddleChild middle = parent.middleBuilder().set(new StringModule("sam")).build();
    OtherMiddleChild other = parent.otherBuilder().set(new StringModule("tara")).build();

    Grandchild middlegrand =
        middle.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(21)).build();
    Grandchild othergrand =
        other.grandchildBuilder().set(new IntModuleIncludingDoubleAndFloat(22)).build();

    assertThat(middle.s()).isEqualTo("sam");
    assertThat(other.s()).isEqualTo("tara");
    assertThat(middlegrand.s()).isEqualTo("sam");
    assertThat(othergrand.s()).isEqualTo("tara");
    assertThat(middlegrand.i()).isEqualTo(21);
    assertThat(othergrand.i()).isEqualTo(22);
  }

  @Test
  public void genericSubcomponentMethod() {
    ParentOfGenericComponent parent =
        DaggerBuilderTest_ParentOfGenericComponent.builder()
            .stringModule(new StringModule("sam"))
            .build();
    Grandchild.Builder builder = parent.subcomponentBuilder();
    Grandchild child = builder.set(new IntModuleIncludingDoubleAndFloat(21)).build();
    assertThat(child.s()).isEqualTo("sam");
    assertThat(child.i()).isEqualTo(21);
  }

  @Test
  public void requireSubcomponentBuilderProviders() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    MiddleChild middle =
        parent
            .requiresMiddleChildBuilder()
            .subcomponentBuilderProvider()
            .get()
            .set(new StringModule("sam"))
            .build();
    Grandchild grandchild =
        middle
            .requiresGrandchildBuilder()
            .subcomponentBuilderProvider()
            .get()
            .set(new IntModuleIncludingDoubleAndFloat(12))
            .build();
    assertThat(middle.s()).isEqualTo("sam");
    assertThat(grandchild.i()).isEqualTo(12);
    assertThat(grandchild.s()).isEqualTo("sam");
  }

  @Test
  public void requireSubcomponentBuilders() {
    ParentComponent parent = DaggerBuilderTest_ParentComponent.create();
    MiddleChild middle =
        parent
            .requiresMiddleChildBuilder()
            .subcomponentBuilder()
            .set(new StringModule("sam"))
            .build();
    Grandchild grandchild =
        middle
            .requiresGrandchildBuilder()
            .subcomponentBuilder()
            .set(new IntModuleIncludingDoubleAndFloat(12))
            .build();
    assertThat(middle.s()).isEqualTo("sam");
    assertThat(grandchild.i()).isEqualTo(12);
    assertThat(grandchild.s()).isEqualTo("sam");
  }
}
