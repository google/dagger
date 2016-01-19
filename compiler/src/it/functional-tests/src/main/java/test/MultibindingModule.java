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

import dagger.Module;
import dagger.Multibindings;
import dagger.Provides;
import dagger.mapkeys.ClassKey;
import dagger.mapkeys.IntKey;
import dagger.mapkeys.LongKey;
import dagger.mapkeys.StringKey;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Provider;

import static dagger.Provides.Type.MAP;
import static dagger.Provides.Type.SET;

@Module
class MultibindingModule {
  @Provides(type = MAP)
  @StringKey("foo")
  static String provideFooKey(double doubleDependency) {
    return "foo value";
  }

  @Provides(type = MAP)
  @StringKey("bar")
  static String provideBarKey() {
    return "bar value";
  }

  @Provides(type = MAP)
  @StringKey("foo")
  static String[] provideFooArrayValue(double doubleDependency) {
    return new String[] {"foo1", "foo2"};
  }

  @Provides(type = MAP)
  @StringKey("bar")
  static String[] provideBarArrayValue() {
    return new String[] {"bar1", "bar2"};
  }

  @Provides(type = SET)
  static int provideFiveToSet() {
    return 5;
  }

  @Provides(type = SET)
  static int provideSixToSet() {
    return 6;
  }

  @Provides
  static Set<String> provideMapKeys(Map<String, Provider<String>> map) {
    return map.keySet();
  }

  @Provides
  static Collection<String> provideMapValues(Map<String, String> map) {
    return map.values();
  }

  @Provides(type = MAP)
  @NestedAnnotationContainer.NestedWrappedKey(Integer.class)
  static String valueForInteger() {
    return "integer";
  }

  @Provides(type = MAP)
  @NestedAnnotationContainer.NestedWrappedKey(Long.class)
  static String valueForLong() {
    return "long";
  }

  @Provides(type = MAP)
  @ClassKey(Integer.class)
  static String valueForClassInteger() {
    return "integer";
  }

  @Provides(type = MAP)
  @ClassKey(Long.class)
  static String valueForClassLong() {
    return "long";
  }

  @Provides(type = MAP)
  @NumberClassKey(BigDecimal.class)
  static String valueForNumberClassBigDecimal() {
    return "bigdecimal";
  }

  @Provides(type = MAP)
  @NumberClassKey(BigInteger.class)
  static String valueForNumberClassBigInteger() {
    return "biginteger";
  }

  @Provides(type = MAP)
  @LongKey(100)
  static String valueFor100Long() {
    return "100 long";
  }

  @Provides(type = MAP)
  @IntKey(100)
  static String valueFor100Int() {
    return "100 int";
  }

  @Provides(type = MAP)
  @ShortKey(100)
  static String valueFor100Short() {
    return "100 short";
  }

  @Provides(type = MAP)
  @ByteKey(100)
  static String valueFor100Byte() {
    return "100 byte";
  }

  @Provides(type = MAP)
  @BooleanKey(true)
  static String valueForTrue() {
    return "true";
  }

  @Provides(type = MAP)
  @CharKey('a')
  static String valueForA() {
    return "a char";
  }

  @Provides(type = MAP)
  @CharKey('\n')
  static String valueForNewline() {
    return "newline char";
  }

  @Provides(type = MAP)
  @UnwrappedAnnotationKey(@StringKey("foo\n"))
  static String valueForUnwrappedAnnotationKeyFoo() {
    return "foo annotation";
  }

  @Provides(type = MAP)
  @WrappedAnnotationKey(
    value = @StringKey("foo"),
    integers = {1, 2, 3},
    annotations = {},
    classes = {Long.class, Integer.class}
  )
  static String valueForWrappedAnnotationKeyFoo() {
    return "wrapped foo annotation";
  }

  @Provides(type = SET)
  @Named("complexQualifier")
  static String valueForComplexQualifierSet() {
    return "foo";
  }

  @Provides(type = SET)
  static CharSequence setContribution() {
    return "foo";
  }

  @Provides(type = SET)
  @Named("complexQualifier")
  static CharSequence qualifiedSetContribution() {
    return "qualified foo";
  }

  @Provides(type = MAP)
  @StringKey("key")
  static CharSequence mapContribution() {
    return "foo value";
  }

  @Provides(type = MAP)
  @Named("complexQualifier")
  @StringKey("key")
  static CharSequence qualifiedMapContribution() {
    return "qualified foo value";
  }

  interface EmptiesSupertype {
    Set<Object> emptySet();

    Map<String, Object> emptyMap();

    Set<CharSequence> maybeEmptySet();

    Map<String, CharSequence> maybeEmptyMap();
  }

  @Multibindings
  interface Empties extends EmptiesSupertype {
    @Named("complexQualifier")
    Set<Object> emptyQualifiedSet();

    @Named("complexQualifier")
    Map<String, Object> emptyQualifiedMap();

    @Named("complexQualifier")
    Set<CharSequence> maybeEmptyQualifiedSet();

    @Named("complexQualifier")
    Map<String, CharSequence> maybeEmptyQualifiedMap();
  }
}
