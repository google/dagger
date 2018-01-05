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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.functional.GenericComponent.NongenericModule;
import dagger.functional.sub.Exposed;
import dagger.functional.sub.PublicSubclass;
import java.util.Arrays;
import java.util.List;
import javax.inject.Provider;

@Component(modules = {ChildDoubleModule.class, ChildIntegerModule.class, NongenericModule.class})
interface GenericComponent {
  ReferencesGeneric referencesGeneric();
  GenericDoubleReferences<A> doubleGenericA();
  GenericDoubleReferences<B> doubleGenericB();
  ComplexGenerics complexGenerics();
  GenericNoDeps<A> noDepsA();
  GenericNoDeps<B> noDepsB();

  void injectA(GenericChild<A> childA);
  void injectB(GenericChild<B> childB);

  Exposed exposed();
  PublicSubclass publicSubclass();
  
  Iterable<Integer> iterableInt();
  Iterable<Double> iterableDouble();

  Provider<List<String>> stringsProvider(); // b/71595104

  // b/71595104
  @Module
  abstract class GenericModule<T> {
    // Note that for subclasses that use String for T, this factory will still need two
    // Provider<String> framework dependencies.
    @Provides
    List<T> list(T t, String string) {
      return Arrays.asList(t);
    }
  }

  // b/71595104
  @Module
  class NongenericModule extends GenericModule<String> {
    @Provides
    static String string() {
      return "string";
    }
  }
}
