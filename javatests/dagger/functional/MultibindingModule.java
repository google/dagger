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

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.LongKey;
import dagger.multibindings.StringKey;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Provider;

@Module
class MultibindingModule {
  @Provides
  @IntoMap
  @StringKey("foo")
  static String provideFooKey(@SuppressWarnings("unused") double doubleDependency) {
    return "foo value";
  }

  @Provides
  @IntoMap
  @StringKey("bar")
  static String provideBarKey() {
    return "bar value";
  }

  @Provides
  @IntoMap
  @StringKey("foo")
  static String[] provideFooArrayValue(@SuppressWarnings("unused") double doubleDependency) {
    return new String[] {"foo1", "foo2"};
  }

  @Provides
  @IntoMap
  @StringKey("bar")
  static String[] provideBarArrayValue() {
    return new String[] {"bar1", "bar2"};
  }

  @Provides
  @IntoSet
  static int provideFiveToSet() {
    return 5;
  }

  @Provides
  @IntoSet
  static int provideSixToSet() {
    return 6;
  }

  @Provides
  @ElementsIntoSet
  static Set<Integer> provideElementsIntoSet() {
    Set<Integer> set = new HashSet<>();
    set.add(-101);
    set.add(-102);
    return set;
  }

  @Provides
  static Set<String> provideMapKeys(Map<String, Provider<String>> map) {
    return map.keySet();
  }

  @Provides
  static Collection<String> provideMapValues(Map<String, String> map) {
    return map.values();
  }

  @Provides
  @IntoMap
  @NestedAnnotationContainer.NestedWrappedKey(Integer.class)
  static String valueForInteger() {
    return "integer";
  }

  @Provides
  @IntoMap
  @NestedAnnotationContainer.NestedWrappedKey(Long.class)
  static String valueForLong() {
    return "long";
  }

  @Provides
  @IntoMap
  @ClassKey(Integer.class)
  static String valueForClassInteger() {
    return "integer";
  }

  @Provides
  @IntoMap
  @ClassKey(Long.class)
  static String valueForClassLong() {
    return "long";
  }

  @Provides
  @IntoMap
  @NumberClassKey(BigDecimal.class)
  static String valueForNumberClassBigDecimal() {
    return "bigdecimal";
  }

  @Provides
  @IntoMap
  @NumberClassKey(BigInteger.class)
  static String valueForNumberClassBigInteger() {
    return "biginteger";
  }

  @Provides
  @IntoMap
  @LongKey(100)
  static String valueFor100Long() {
    return "100 long";
  }

  @Provides
  @IntoMap
  @IntKey(100)
  static String valueFor100Int() {
    return "100 int";
  }

  @Provides
  @IntoMap
  @ShortKey(100)
  static String valueFor100Short() {
    return "100 short";
  }

  @Provides
  @IntoMap
  @ByteKey(100)
  static String valueFor100Byte() {
    return "100 byte";
  }

  @Provides
  @IntoMap
  @BooleanKey(true)
  static String valueForTrue() {
    return "true";
  }

  @Provides
  @IntoMap
  @CharKey('a')
  static String valueForA() {
    return "a char";
  }

  @Provides
  @IntoMap
  @CharKey('\n')
  static String valueForNewline() {
    return "newline char";
  }

  @Provides
  @IntoMap
  @UnwrappedAnnotationKey(@StringKey("foo\n"))
  static String valueForUnwrappedAnnotationKeyFoo() {
    return "foo annotation";
  }

  @Provides
  @IntoMap
  @WrappedAnnotationKey(
    value = @StringKey("foo"),
    integers = {1, 2, 3},
    annotations = {},
    classes = {Long.class, Integer.class}
  )
  static String valueForWrappedAnnotationKeyFoo() {
    return "wrapped foo annotation";
  }

  @Provides
  @IntoSet
  @Named("complexQualifier")
  static String valueForComplexQualifierSet() {
    return "foo";
  }

  @Provides
  @IntoSet
  static CharSequence setContribution() {
    return "foo";
  }

  @Provides
  @IntoSet
  @Named("complexQualifier")
  static CharSequence qualifiedSetContribution() {
    return "qualified foo";
  }

  @Provides
  @IntoMap
  @StringKey("key")
  static CharSequence mapContribution() {
    return "foo value";
  }

  @Provides
  @IntoMap
  @Named("complexQualifier")
  @StringKey("key")
  static CharSequence qualifiedMapContribution() {
    return "qualified foo value";
  }
}
