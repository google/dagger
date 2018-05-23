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
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuilderTest {

  @Test public void interfaceBuilder() {
    TestComponentWithBuilderInterface.Builder builder =
        DaggerTestComponentWithBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.intModule(new IntModuleIncludingDoubleAndFloat(1))
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

  @Test public void abstractClassBuilder() {
    TestComponentWithBuilderAbstractClass.Builder builder =
        TestComponentWithBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.intModule(new IntModuleIncludingDoubleAndFloat(1))
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

  @Test public void interfaceGenericBuilder() {
    TestComponentWithGenericBuilderInterface.Builder builder =
        DaggerTestComponentWithGenericBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.setM2(new IntModuleIncludingDoubleAndFloat(1))
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

  @Test public void abstractClassGenericBuilder() {
    TestComponentWithGenericBuilderAbstractClass.Builder builder =
        DaggerTestComponentWithGenericBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.setM2(new IntModuleIncludingDoubleAndFloat(1))
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
  
  @Test public void subcomponents_interface() {
    ParentComponent parent = DaggerParentComponent.create();    
    TestChildComponentWithBuilderInterface.Builder builder1 = parent.childInterfaceBuilder();
    try {
      builder1.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder1.setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .set(new ByteModule((byte)7));
    builder1.set(new FloatModule());
    TestChildComponentWithBuilderInterface child1 = builder1.build();
    assertThat(child1.s()).isEqualTo("sam");
    assertThat(child1.i()).isEqualTo(1);
    assertThat(child1.d()).isEqualTo(4.2d);
    assertThat(child1.f()).isEqualTo(5.5f);
    assertThat(child1.l()).isEqualTo(6L);
    assertThat(child1.b()).isEqualTo((byte)7);
  }
  
  @Test public void subcomponents_abstractclass() {
    ParentComponent parent = DaggerParentComponent.create();
    TestChildComponentWithBuilderAbstractClass.Builder builder2 =
        parent.childAbstractClassBuilder();
    try {
      builder2.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder2.setM2(new IntModuleIncludingDoubleAndFloat(10))
        .setM1(new StringModule("tara"))
        .set(new ByteModule((byte)70));
    builder2.set(new FloatModule());
    TestChildComponentWithBuilderAbstractClass child2 = builder2.build();
    assertThat(child2.s()).isEqualTo("tara");
    assertThat(child2.i()).isEqualTo(10);
    assertThat(child2.d()).isEqualTo(4.2d);
    assertThat(child2.f()).isEqualTo(5.5f);
    assertThat(child2.l()).isEqualTo(6L);
    assertThat(child2.b()).isEqualTo((byte)70);
  }
    
  @Test
  public void grandchildren() {
    ParentComponent parent = DaggerParentComponent.create();
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
    ParentComponent parent = DaggerParentComponent.create();
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
        DaggerParentOfGenericComponent.builder().stringModule(new StringModule("sam")).build();
    Grandchild.Builder builder = parent.subcomponentBuilder();
    Grandchild child = builder.set(new IntModuleIncludingDoubleAndFloat(21)).build();
    assertThat(child.s()).isEqualTo("sam");
    assertThat(child.i()).isEqualTo(21);
  }
  
  @Test
  public void requireSubcomponentBuilderProviders() {
    ParentComponent parent = DaggerParentComponent.create();
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
    ParentComponent parent = DaggerParentComponent.create();
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
