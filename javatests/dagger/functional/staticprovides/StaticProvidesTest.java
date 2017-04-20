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

package dagger.functional.staticprovides;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticProvidesTest {
  @Parameters
  public static Collection<Object[]> components() {
    return Arrays.asList(new Object[][] {
        {DaggerStaticTestComponent.create()},
        {DaggerStaticTestComponentWithBuilder.builder().build()},
        {DaggerStaticTestComponentWithBuilder.builder()
          .allStaticModule(new AllStaticModule())
          .someStaticModule(new SomeStaticModule())
          .build()}});
  }

  @Parameter
  public StaticTestComponent component;

  @Test public void setMultibinding() {
    assertThat(component.getMultiboundStrings()).isEqualTo(ImmutableSet.of(
        AllStaticModule.class + ".contributeString",
        SomeStaticModule.class + ".contributeStringFromAStaticMethod",
        SomeStaticModule.class + ".contributeStringFromAnInstanceMethod"));
  }

  @Test public void allStaticProvidesModules_noFieldInComponentBuilder() {
    for (Field field : DaggerStaticTestComponent.Builder.class.getDeclaredFields()) {
      assertWithMessage(field.getName())
          .that(field.getType()).isNotEqualTo(AllStaticModule.class);
    }
  }

  @Test public void allStaticProvidesModules_deprecatedMethodInComponentBuilder() {
    for (Method method : DaggerStaticTestComponent.Builder.class.getDeclaredMethods()) {
      if (Arrays.asList(method.getParameterTypes()).contains(AllStaticModule.class)) {
        assertWithMessage(method.getName())
            .that(method.isAnnotationPresent(Deprecated.class))
            .isTrue();
      }
    }
  }
}
