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

package dagger.functional;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import dagger.functional.sub.Exposed;
import dagger.functional.sub.PublicSubclass;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GenericTest {

  @Test public void testGenericComponentCreate() {
    GenericComponent component = DaggerGenericComponent.create();
    assertThat(component).isNotNull();
  }
  
  @Test public void testGenericSimpleReferences() {
    GenericComponent component = DaggerGenericComponent.create();
    assertThat(component.referencesGeneric().genericA.t).isNotNull();    
  }
  
  @Test public void testGenericDoubleReferences() {
    GenericComponent component = DaggerGenericComponent.create();
    GenericDoubleReferences<A> doubleA = component.doubleGenericA();
    assertThat(doubleA.a).isNotNull();
    assertThat(doubleA.a2).isNotNull();
    assertThat(doubleA.t).isNotNull();
    assertThat(doubleA.t2).isNotNull();

    GenericDoubleReferences<B> doubleB = component.doubleGenericB();
    assertThat(doubleB.a).isNotNull();
    assertThat(doubleB.a2).isNotNull();
    assertThat(doubleB.t).isNotNull();
    assertThat(doubleB.t2).isNotNull();
  }
  
  @Test public void complexGenerics() {
    GenericComponent component = DaggerGenericComponent.create();
    // validate these can be called w/o exceptions.
    component.complexGenerics();
  }
  
  @Test public void noDepsGenerics() {
    GenericComponent component = DaggerGenericComponent.create();
    // validate these can be called w/o exceptions.
    component.noDepsA();
    component.noDepsB();
  }
  
  @Test public void boundedGenerics() {
    BoundedGenericModule expected = new BoundedGenericModule();
    BoundedGenericComponent component = DaggerBoundedGenericComponent.create();
    BoundedGenerics<Integer, ArrayList<String>, LinkedList<CharSequence>, Integer, List<Integer>>
        b1 = component.bounds1();
    assertEquals(expected.provideInteger(), b1.a);
    assertEquals(expected.provideArrayListString(), b1.b);
    assertEquals(expected.provideLinkedListCharSeq(), b1.c);
    assertEquals(expected.provideInteger(), b1.d);
    assertEquals(expected.provideListOfInteger(), b1.e);

    BoundedGenerics<Double, LinkedList<String>, LinkedList<Comparable<String>>, Double, Set<Double>>
        b2 = component.bounds2();
    assertEquals(expected.provideDouble(), b2.a);
    assertEquals(expected.provideLinkedListString(), b2.b);
    assertEquals(expected.provideArrayListOfComparableString(), b2.c);
    assertEquals(expected.provideDouble(), b2.d);
    assertEquals(expected.provideSetOfDouble(), b2.e);
  }
  
  @Test public void membersInjections() {
    GenericComponent component = DaggerGenericComponent.create();
    GenericChild<A> childA = new GenericChild<A>();
    component.injectA(childA);
    assertThat(childA.a).isNotNull();
    assertThat(childA.b).isNotNull();
    assertThat(childA.registeredA).isNotNull();
    assertThat(childA.registeredB).isNotNull();
    assertThat(childA.registeredT).isNotNull();
    assertThat(childA.registeredX).isNotNull();
    assertThat(childA.registeredY).isNotNull();
    
    GenericChild<B> childB = new GenericChild<B>();
    component.injectB(childB);
    assertThat(childB.a).isNotNull();
    assertThat(childB.b).isNotNull();
    assertThat(childB.registeredA).isNotNull();
    assertThat(childB.registeredB).isNotNull();
    assertThat(childB.registeredT).isNotNull();
    assertThat(childB.registeredX).isNotNull();
    assertThat(childB.registeredY).isNotNull();
  }
  
  @Test public void packagePrivateTypeParameterDependencies() {
    GenericComponent component = DaggerGenericComponent.create();
    Exposed exposed = component.exposed();
    assertThat(exposed.gpp.t).isNotNull();
    assertThat(exposed.gpp2).isNotNull();
  }
  
  @SuppressWarnings("rawtypes")
  @Test public void publicSubclassWithPackagePrivateTypeParameterOfSuperclass() {
    GenericComponent component = DaggerGenericComponent.create();
    PublicSubclass publicSubclass = component.publicSubclass();
    assertThat(((Generic)publicSubclass).t).isNotNull();
  }

  @Test public void singletonScopesAppliesToEachResolvedType() {
    SingletonGenericComponent component = DaggerSingletonGenericComponent.create();
    ScopedGeneric<A> a = component.scopedGenericA();
    assertThat(a).isSameInstanceAs(component.scopedGenericA());
    assertThat(a.t).isNotNull();

    ScopedGeneric<B> b = component.scopedGenericB();
    assertThat(b).isSameInstanceAs(component.scopedGenericB());
    assertThat(b.t).isNotNull();

    assertThat(a).isNotSameInstanceAs(b);
  }

  @Test // See https://github.com/google/dagger/issues/671
  public void scopedSimpleGenerics() {
    SingletonGenericComponent component = DaggerSingletonGenericComponent.create();
    ScopedSimpleGeneric<A> a = component.scopedSimpleGenericA();
    assertThat(a).isSameInstanceAs(component.scopedSimpleGenericA());

    ScopedSimpleGeneric<B> b = component.scopedSimpleGenericB();
    assertThat(b).isSameInstanceAs(component.scopedSimpleGenericB());

    assertThat(a).isNotSameInstanceAs(b);
  }
  
  @Test public void genericModules() {
    GenericComponent component = DaggerGenericComponent.create();
    assertThat(component.iterableInt()).containsExactly(1, 2).inOrder();
    assertThat(component.iterableDouble()).containsExactly(3d, 4d).inOrder();
  }
}
