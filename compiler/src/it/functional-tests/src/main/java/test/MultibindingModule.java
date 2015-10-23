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
import dagger.Provides;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;

import static dagger.Provides.Type.MAP;
import static dagger.Provides.Type.SET;

@Module
class MultibindingModule {
  @Provides(type = MAP)
  @TestStringKey("foo")
  String provideFooKey(double doubleDependency) {
    return "foo value";
  }

  @Provides(type = MAP)
  @TestStringKey("bar")
  String provideBarKey() {
    return "bar value";
  }

  @Provides(type = SET) int provideFiveToSet() {
    return 5;
  }

  @Provides(type = SET) int provideSixToSet() {
    return 6;
  }

  @Provides Set<String> provideMapKeys(Map<String, Provider<String>> map) {
    return map.keySet();
  }

  @Provides Collection<String> provideMapValues(Map<String, String> map) {
    return map.values();
  }

  @Provides(type = MAP)
  @TestStringKey.NestedWrappedKey(Integer.class)
  String valueForInteger() {
    return "integer";
  }

  @Provides(type = MAP)
  @TestStringKey.NestedWrappedKey(Long.class)
  String valueForLong() {
    return "long";
  }

  @Provides(type = MAP)
  @TestClassKey(Integer.class)
  String valueForClassInteger() {
    return "integer";
  }

  @Provides(type = MAP)
  @TestClassKey(Long.class)
  String valueForClassLong() {
    return "long";
  }

  @Provides(type = MAP)
  @TestNumberClassKey(BigDecimal.class)
  String valueForNumberClassBigDecimal() {
    return "bigdecimal";
  }

  @Provides(type = MAP)
  @TestNumberClassKey(BigInteger.class)
  String valueForNumberClassBigInteger() {
    return "biginteger";
  }

  @Provides(type = MAP)
  @TestLongKey(longValue = 100)
  String valueFor100Long() {
    return "100 long";
  }

  @Provides(type = MAP)
  @TestIntKey(100)
  String valueFor100Int() {
    return "100 int";
  }

  @Provides(type = MAP)
  @TestShortKey(100)
  String valueFor100Short() {
    return "100 short";
  }

  @Provides(type = MAP)
  @TestByteKey(100)
  String valueFor100Byte() {
    return "100 byte";
  }

  @Provides(type = MAP)
  @TestBooleanKey(true)
  String valueForTrue() {
    return "true";
  }

  @Provides(type = MAP)
  @TestCharKey('a')
  String valueForA() {
    return "a char";
  }

  @Provides(type = MAP)
  @TestCharKey('\n')
  String valueForNewline() {
    return "newline char";
  }

  @Provides(type = MAP)
  @TestUnwrappedAnnotationKey(@TestStringKey("foo\n"))
  String valueForUnwrappedAnnotationKeyFoo() {
    return "foo annotation";
  }

  @Provides(type = MAP)
  @TestWrappedAnnotationKey(
    value = @TestStringKey("foo"),
    integers = {1, 2, 3},
    annotations = {},
    classes = {Long.class, Integer.class}
  )
  String valueForWrappedAnnotationKeyFoo() {
    return "wrapped foo annotation";
  }
}
