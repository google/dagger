/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.spi;

import static com.google.common.io.Resources.getResource;
import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpiTest {

  @Component(modules = M.class)
  interface C {
    String string();
  }

  @Module
  abstract static class M {
    @Provides
    static String string() {
      return "string";
    }
  }

  @Test
  public void testPluginRuns() throws IOException {
    Properties properties = new Properties();
    try (InputStream stream = getResource(SpiTest.class, "SpiTest_C.properties").openStream()) {
      properties.load(stream);
    }
    assertThat(properties).containsEntry("component[0]", C.class.getCanonicalName());
  }
}
