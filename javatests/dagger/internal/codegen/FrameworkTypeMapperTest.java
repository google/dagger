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
import static dagger.model.RequestKind.INSTANCE;
import static dagger.model.RequestKind.LAZY;
import static dagger.model.RequestKind.PRODUCED;
import static dagger.model.RequestKind.PRODUCER;
import static dagger.model.RequestKind.PROVIDER;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link FrameworkTypeMapper}. */
@RunWith(JUnit4.class)
public class FrameworkTypeMapperTest {
  @Test public void forProvider() {
    FrameworkTypeMapper mapper = FrameworkTypeMapper.FOR_PROVIDER;
    assertThat(mapper.getFrameworkType(INSTANCE)).isEqualTo(FrameworkType.PROVIDER);
    assertThat(mapper.getFrameworkType(LAZY)).isEqualTo(FrameworkType.PROVIDER);
    assertThat(mapper.getFrameworkType(PROVIDER)).isEqualTo(FrameworkType.PROVIDER);
  }

  @Test public void forProducer() {
    FrameworkTypeMapper mapper = FrameworkTypeMapper.FOR_PRODUCER;
    assertThat(mapper.getFrameworkType(INSTANCE)).isEqualTo(FrameworkType.PRODUCER_NODE);
    assertThat(mapper.getFrameworkType(LAZY)).isEqualTo(FrameworkType.PROVIDER);
    assertThat(mapper.getFrameworkType(PROVIDER)).isEqualTo(FrameworkType.PROVIDER);
    assertThat(mapper.getFrameworkType(PRODUCER)).isEqualTo(FrameworkType.PRODUCER_NODE);
    assertThat(mapper.getFrameworkType(PRODUCED)).isEqualTo(FrameworkType.PRODUCER_NODE);
  }
}
