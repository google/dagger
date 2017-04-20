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
import dagger.functional.sub.ContributionsModule;
import dagger.multibindings.StringKey;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Provider;

@Component(
  modules = {MultibindingModule.class, MultibindsModule.class, ContributionsModule.class},
  dependencies = MultibindingDependency.class
)
interface MultibindingComponent {
  Map<String, String> map();
  Map<String, String[]> mapOfArrays();
  Map<String, Provider<String>> mapOfProviders();
  Set<String> mapKeys();
  Collection<String> mapValues();
  Set<Integer> set();
  Map<NestedAnnotationContainer.NestedWrappedKey, String> nestedKeyMap();
  Map<Class<? extends Number>, String> numberClassKeyMap();
  Map<Class<?>, String> classKeyMap();
  Map<Long, String> longKeyMap();
  Map<Integer, String> integerKeyMap();
  Map<Short, String> shortKeyMap();
  Map<Byte, String> byteKeyMap();
  Map<Boolean, String> booleanKeyMap();
  Map<Character, String> characterKeyMap();
  Map<StringKey, String> unwrappedAnnotationKeyMap();
  Map<WrappedAnnotationKey, String> wrappedAnnotationKeyMap();
  @Named("complexQualifier") Set<String> complexQualifierStringSet();
  Set<Object> emptySet();

  @Named("complexQualifier")
  Set<Object> emptyQualifiedSet();

  Map<String, Object> emptyMap();

  @Named("complexQualifier")
  Map<String, Object> emptyQualifiedMap();

  Set<CharSequence> maybeEmptySet();

  @Named("complexQualifier")
  Set<CharSequence> maybeEmptyQualifiedSet();

  Map<String, CharSequence> maybeEmptyMap();

  @Named("complexQualifier")
  Map<String, CharSequence> maybeEmptyQualifiedMap();
}
