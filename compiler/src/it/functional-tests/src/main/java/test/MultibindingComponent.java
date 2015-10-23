/*
* Copyright (C) 2015 Google, Inc.
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
package test;

import dagger.Component;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import test.sub.ContributionsModule;

@Component(
  modules = {
    MultibindingModule.class,
    ContributionsModule.class
  },
  dependencies = MultibindingDependency.class
)
interface MultibindingComponent {
  Map<String, String> map();
  Map<String, Provider<String>> mapOfProviders();
  Set<String> mapKeys();
  Collection<String> mapValues();
  Set<Integer> set();
  Map<TestStringKey.NestedWrappedKey, String> nestedKeyMap();
  Map<Class<? extends Number>, String> numberClassKeyMap();
  Map<Class<?>, String> classKeyMap();
  Map<Long, String> longKeyMap();
  Map<Integer, String> integerKeyMap();
  Map<Short, String> shortKeyMap();
  Map<Byte, String> byteKeyMap();
  Map<Boolean, String> booleanKeyMap();
  Map<Character, String> characterKeyMap();
  Map<TestStringKey, String> unwrappedAnnotationKeyMap();
  Map<TestWrappedAnnotationKey, String> wrappedAnnotationKeyMap();
}
