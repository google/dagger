/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.BindingType.MEMBERS_INJECTION;
import static dagger.internal.codegen.BindingType.PRODUCTION;
import static dagger.internal.codegen.BindingType.PROVISION;
import static dagger.internal.codegen.DependencyRequest.Kind.INSTANCE;
import static dagger.internal.codegen.DependencyRequest.Kind.LAZY;
import static dagger.internal.codegen.DependencyRequest.Kind.MEMBERS_INJECTOR;
import static dagger.internal.codegen.DependencyRequest.Kind.PRODUCED;
import static dagger.internal.codegen.DependencyRequest.Kind.PRODUCER;
import static dagger.internal.codegen.DependencyRequest.Kind.PROVIDER;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link BindingTypeMapper}.
 */
@RunWith(JUnit4.class)
public class BindingTypeMapperTest {
  @Test public void forProvider() {
    BindingTypeMapper mapper = BindingTypeMapper.FOR_PROVIDER;
    assertThat(mapper.getBindingType(INSTANCE))
        .isEqualTo(PROVISION);
    assertThat(mapper.getBindingType(LAZY))
        .isEqualTo(PROVISION);
    assertThat(mapper.getBindingType(PROVIDER))
        .isEqualTo(PROVISION);
    assertThat(mapper.getBindingType(MEMBERS_INJECTOR))
        .isEqualTo(MEMBERS_INJECTION);
  }

  @Test public void forProducer() {
    BindingTypeMapper mapper = BindingTypeMapper.FOR_PRODUCER;
    assertThat(mapper.getBindingType(INSTANCE))
        .isEqualTo(PRODUCTION);
    assertThat(mapper.getBindingType(LAZY))
        .isEqualTo(PROVISION);
    assertThat(mapper.getBindingType(PROVIDER))
        .isEqualTo(PROVISION);
    assertThat(mapper.getBindingType(MEMBERS_INJECTOR))
        .isEqualTo(MEMBERS_INJECTION);
    assertThat(mapper.getBindingType(PRODUCER))
        .isEqualTo(PRODUCTION);
    assertThat(mapper.getBindingType(PRODUCED))
        .isEqualTo(PRODUCTION);
  }
}
