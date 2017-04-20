/*
 * Copyright (C) 2016 The Dagger Authors.
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
import static dagger.functional.ReleasableReferencesComponents.Thing.thing;

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.testing.GcFinalization;
import com.google.common.testing.GcFinalization.FinalizationPredicate;
import dagger.functional.ReleasableReferencesComponents.Child;
import dagger.functional.ReleasableReferencesComponents.ChildModule;
import dagger.functional.ReleasableReferencesComponents.ChildRegularScope;
import dagger.functional.ReleasableReferencesComponents.ChildReleasableScope1;
import dagger.functional.ReleasableReferencesComponents.ChildReleasableScope2;
import dagger.functional.ReleasableReferencesComponents.ChildReleasableScope3;
import dagger.functional.ReleasableReferencesComponents.Metadata1;
import dagger.functional.ReleasableReferencesComponents.Parent;
import dagger.functional.ReleasableReferencesComponents.ParentModule;
import dagger.functional.ReleasableReferencesComponents.ParentRegularScope;
import dagger.functional.ReleasableReferencesComponents.ParentReleasableScope1;
import dagger.functional.ReleasableReferencesComponents.ParentReleasableScope2;
import dagger.functional.ReleasableReferencesComponents.Thing;
import dagger.functional.ReleasableReferencesComponents.ThingComponent;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReleasableReferencesComponentsTest {

  private Parent component;
  private ParentAsserts parentAsserts;
  private ChildAsserts childAsserts;

  @Before
  public void setUp() {
    component = DaggerReleasableReferencesComponents_Parent.create();
    parentAsserts = new ParentAsserts(component);
    childAsserts = parentAsserts.newChildAsserts();
  }

  @Test
  public void releasableReferenceManagers() {
    ImmutableMap<Class<? extends Annotation>, ReleasableReferenceManager> managers =
        Maps.uniqueIndex(
            component.managers(),
            new Function<ReleasableReferenceManager, Class<? extends Annotation>>() {
              @Override
              public Class<? extends Annotation> apply(
                  ReleasableReferenceManager releasableReferenceManager) {
                return releasableReferenceManager.scope();
              }
            });
    assertThat(managers)
        .containsEntry(ParentReleasableScope1.class, component.parentReleasableScope1Manager());
    assertThat(managers)
        .containsEntry(ParentReleasableScope2.class, component.parentReleasableScope2Manager());
    assertThat(managers)
        .containsEntry(ChildReleasableScope1.class, component.childReleasableScope1Manager());
    assertThat(managers)
        .containsEntry(ChildReleasableScope2.class, component.childReleasableScope2Manager());
    // Should contain a manager for ChildReleasableScope3 even though
    // @ForReleasableReferences(Scope5.class) isn't needed.
    assertThat(managers).containsKey(ChildReleasableScope3.class);
  }

  @Test
  public void setOfTypedReleasableReferenceManagers() {
    ListMultimap<Class<? extends Annotation>, Metadata1> managers =
        Multimaps.transformValues(
            Multimaps.index(
                component.typedReleasableReferenceManagers1(),
                new Function<TypedReleasableReferenceManager<?>, Class<? extends Annotation>>() {
                  @Override
                  public Class<? extends Annotation> apply(
                      TypedReleasableReferenceManager<?> releasableReferenceManager) {
                    return releasableReferenceManager.scope();
                  }
                }),
            new Function<TypedReleasableReferenceManager<Metadata1>, Metadata1>() {
              @Override
              public Metadata1 apply(TypedReleasableReferenceManager<Metadata1> manager) {
                return manager.metadata();
              }
            });
    assertThat(managers)
        .containsEntry(ParentReleasableScope2.class, metadata1("ParentReleasableScope2"));
    assertThat(managers)
        .containsEntry(ChildReleasableScope2.class, metadata1("ChildReleasableScope2.1"));
    assertThat(managers)
        .containsEntry(ChildReleasableScope3.class, metadata1("ChildReleasableScope3.1"));
  }

  @AutoAnnotation
  static Metadata1 metadata1(String value) {
    return new AutoAnnotation_ReleasableReferencesComponentsTest_metadata1(value);
  }

  @Test
  public void basicScopingWorks() {
    assertBindingCallCounts();
    // assert again to make sure that the scoped bindings aren't called again
    assertBindingCallCounts();
  }

  @Test
  public void releaseThenGc() {
    assertBindingCallCounts();
    component.parentReleasableScope1Manager().releaseStrongReferences(); // release scope 1
    assertBindingCallCounts(); // no change to scoped bindings
    gcAndWaitUntilWeakReferencesCleared(
        ParentModule.class, ChildModule.class, ParentReleasableScope1.class); // GC
    parentAsserts.expectedCallsForParentReleasableScope1Thing++; // expect scope 1 bindings again
    assertBindingCallCounts();
  }

  @Test
  public void releaseThenRestoreThenGcThenRelease() {
    assertBindingCallCounts();
    component.parentReleasableScope2Manager().releaseStrongReferences(); // release scope 2
    assertBindingCallCounts(); // no change to scoped bindings
    component.parentReleasableScope2Manager().restoreStrongReferences(); // restore scope 2
    assertBindingCallCounts(); // no change to scoped bindings
    gcAndWaitUntilWeakReferencesCleared(ParentModule.class, ChildModule.class); // GC
    assertBindingCallCounts(); // no change to scoped bindings

    // Releasing again and GCing again means the binding is executed again.
    component.parentReleasableScope2Manager().releaseStrongReferences(); // release scope 2
    assertBindingCallCounts(); // no change to scoped bindings
    gcAndWaitUntilWeakReferencesCleared(
        ParentModule.class, ChildModule.class, ParentReleasableScope2.class); // GC
    parentAsserts.expectedCallsForParentReleasableScope2Thing++; // expect scope 2 bindings again
    assertBindingCallCounts();
  }

  @Test
  public void subcomponentReleaseThenGc() {
    assertBindingCallCounts();
    component.childReleasableScope1Manager().releaseStrongReferences(); // release scope 3
    assertBindingCallCounts(); // no change to scoped bindings
    gcAndWaitUntilWeakReferencesCleared(
        ParentModule.class, ChildModule.class, ChildReleasableScope1.class); // GC
    childAsserts.expectedCallsForChildReleasableScope1Thing++; // expect scope 3 bindings again
    assertBindingCallCounts();
  }

  @Test
  public void subcomponentReleaseThenRestoreThenGcThenRelease() {
    assertBindingCallCounts();
    component.childReleasableScope2Manager().releaseStrongReferences(); // release scope 4
    assertBindingCallCounts(); // no change to scoped bindings
    component.childReleasableScope2Manager().restoreStrongReferences(); // restore scope 4
    gcAndWaitUntilWeakReferencesCleared(ParentModule.class, ChildModule.class); // GC
    assertBindingCallCounts(); // no change to scoped bindings
    component.childReleasableScope2Manager().releaseStrongReferences(); // release scope 4
    gcAndWaitUntilWeakReferencesCleared(
        ParentModule.class, ChildModule.class, ChildReleasableScope2.class); // GC
    childAsserts.expectedCallsForChildReleasableScope2Thing++; // expect scope 4 bindings again
    assertBindingCallCounts();
  }

  @Test
  public void twoInstancesOfSameSubcomponent() {
    // Two instances of the same subcomponent.
    ChildAsserts child2Asserts = parentAsserts.newChildAsserts();
    childAsserts.assertBindingCallCounts();
    child2Asserts.assertBindingCallCounts();

    component.childReleasableScope1Manager().releaseStrongReferences(); // release scope 3
    childAsserts.assertBindingCallCounts(); // no change to scoped bindings in child 1
    child2Asserts.assertBindingCallCounts(); // no change to scoped bindings in child 2
    gcAndWaitUntilWeakReferencesCleared(
        ParentModule.class, ChildModule.class, ChildReleasableScope1.class); // GC
    childAsserts.expectedCallsForChildReleasableScope1Thing++; // expect scope 3 bindings again
    childAsserts.assertBindingCallCounts(); // when calling child.things()
    child2Asserts.expectedCallsForChildReleasableScope1Thing++; // expect scope 3 bindings yet again
    child2Asserts.assertBindingCallCounts(); // when calling child2.things()
  }

  private void assertBindingCallCounts() {
    parentAsserts.assertBindingCallCounts();
    childAsserts.assertBindingCallCounts();
  }

  /**
   * Tries to run garbage collection, and waits for the {@link WeakReference}s to the {@link Thing}s
   * in the maps last returned by {@link Parent#things()} and {@link Child#things()} for {@code
   * keys} to be cleared.
   */
  void gcAndWaitUntilWeakReferencesCleared(final Class<?>... keys) {
    GcFinalization.awaitDone(
        new FinalizationPredicate() {
          @Override
          public boolean isDone() {
            for (Class<?> key : keys) {
              if (parentAsserts.weakThingReferenceUncollected(key)
                  || childAsserts.weakThingReferenceUncollected(key)) {
                return false;
              }
            }
            return true;
          }
        });
  }

  /**
   * Asserts that the map of {@link Thing}s in a {@link ThingComponent} matches expected values. Can
   * also tell when certain values in the map have been finalized.
   */
  private abstract static class ThingAsserts {

    private final ThingComponent component;
    private ImmutableMap<Class<?>, WeakReference<Thing>> weakThings = ImmutableMap.of();

    protected ThingAsserts(ThingComponent component) {
      this.component = component;
    }

    /**
     * Asserts that {@code component.things()} returns an expected map. Each time this is called,
     * the current values in the map are wrapped in {@link WeakReference}s so we can {@linkplain
     * #weakThingReferenceUncollected(Object) check whether they've been cleared} later.
     */
    final void assertBindingCallCounts() {
      Map<Class<?>, Thing> things = component.things();
      assertThat(things).containsExactlyEntriesIn(expectedThingMap());
      weakThings =
          ImmutableMap.copyOf(
              Maps.transformValues(
                  things,
                  new Function<Thing, WeakReference<Thing>>() {
                    @Override
                    public WeakReference<Thing> apply(Thing thing) {
                      return new WeakReference<>(thing);
                    }
                  }));
    }

    /** Returns the expected map. */
    protected abstract ImmutableMap<Class<?>, Thing> expectedThingMap();

    /**
     * Returns {@code true} if the {@link WeakReference} to the {@link Thing} in the map returned by
     * the last call to {@link #assertBindingCallCounts()} for the given key has not been cleared.
     */
    boolean weakThingReferenceUncollected(Object key) {
      WeakReference<Thing> weakThing = weakThings.get(key);
      return weakThing != null && weakThing.get() != null;
    }
  }

  /** Asserts for the {@link Thing}s returned by {@link Parent#things()}. */
  private static final class ParentAsserts extends ThingAsserts {
    final Parent parent;

    /**
     * The number of times we expect the {@code @Provides @IntoMap @ClassKey(ParentModule.class)
     * Thing} provider to have been called.
     */
    int expectedCallsForParentUnscopedThing;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ParentRegularScope.class) Thing} provider to have been
     * called.
     */
    int expectedCallsForParentRegularScopeThing = 1;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ParentReleasableScope1.class) Thing} provider to have
     * been called.
     */
    int expectedCallsForParentReleasableScope1Thing = 1;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ParentReleasableScope2.class) Thing} provider to have
     * been called.
     */
    int expectedCallsForParentReleasableScope2Thing = 1;

    ParentAsserts(Parent parent) {
      super(parent);
      this.parent = parent;
    }

    /**
     * Returns an object that can make assertions for the {@link Thing}s returned by {@link
     * Child#things()}.
     */
    ChildAsserts newChildAsserts() {
      return new ChildAsserts(this, parent.child());
    }

    @Override
    protected ImmutableMap<Class<?>, Thing> expectedThingMap() {
      ++expectedCallsForParentUnscopedThing; // unscoped Thing @Provides method is always called
      return ImmutableMap.of(
          ParentModule.class, thing(expectedCallsForParentUnscopedThing),
          ParentRegularScope.class, thing(expectedCallsForParentRegularScopeThing),
          ParentReleasableScope1.class, thing(expectedCallsForParentReleasableScope1Thing),
          ParentReleasableScope2.class, thing(expectedCallsForParentReleasableScope2Thing));
    }
  }

  /** Asserts for the {@link Thing}s returned by {@link Child#things()}. */
  private static final class ChildAsserts extends ThingAsserts {
    final ParentAsserts parentAsserts;

    /**
     * The number of times we expect the {@code @Provides @IntoMap @ClassKey(ChildModule.class)
     * Thing} provider to have been called.
     */
    int expectedCallsForChildUnscopedThing;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ChildRegularScope.class) Thing} provider to have been
     * called.
     */
    int expectedCallsForChildRegularScopeThing = 1;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ChildReleasableScope1.class) Thing} provider to have been
     * called.
     */
    int expectedCallsForChildReleasableScope1Thing = 1;

    /**
     * The number of times we expect the
     * {@code @Provides @IntoMap @ClassKey(ChildReleasableScope2.class) Thing} provider to have been
     * called.
     */
    int expectedCallsForChildReleasableScope2Thing = 1;

    ChildAsserts(ParentAsserts parentAsserts, Child child) {
      super(child);
      this.parentAsserts = parentAsserts;
    }

    @Override
    protected ImmutableMap<Class<?>, Thing> expectedThingMap() {
      ++expectedCallsForChildUnscopedThing; // unscoped Thing @Provides method is always called
      return new ImmutableMap.Builder<Class<?>, Thing>()
          .putAll(parentAsserts.expectedThingMap())
          .put(ChildModule.class, thing(expectedCallsForChildUnscopedThing))
          .put(ChildRegularScope.class, thing(expectedCallsForChildRegularScopeThing))
          .put(ChildReleasableScope1.class, thing(expectedCallsForChildReleasableScope1Thing))
          .put(ChildReleasableScope2.class, thing(expectedCallsForChildReleasableScope2Thing))
          .build();
    }
  }
}
