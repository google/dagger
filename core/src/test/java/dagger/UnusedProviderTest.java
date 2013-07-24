/*
 * Copyright (C) 2013 Square Inc.
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
package dagger;

import dagger.internal.TestingLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class UnusedProviderTest {

  @Test public void unusedProvidesMethod_whenModuleLibrary_passes() throws Exception {
    class EntryPoint {
    }
    class BagOfMoney {
    }
    @Module(injects = EntryPoint.class, library = true) class TestModule {
      @Provides BagOfMoney providesMoney() {
        return new BagOfMoney();
      }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    graph.validate();
  }

  @Test public void unusedProviderMethod_whenNotLibraryModule_fails() throws Exception {
    class EntryPoint {
    }
    class BagOfMoney {
    }

    @Module(injects = EntryPoint.class) class TestModule {
      @Provides BagOfMoney providesMoney() {
        return new BagOfMoney();
      }
    }

    try {
      ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
      graph.validate();
      fail("Validation should have exploded!");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void whenLibraryModulePlussedToNecessaryModule_shouldNotFailOnUnusedLibraryModule()
      throws Exception {
    class EntryPoint {
    }
    class BagOfMoney {
    }

    @Module(injects = EntryPoint.class, library = true) class ExampleLibraryModule {
      @Provides BagOfMoney providesMoney() {
        return new BagOfMoney();
      }
    }

    @Module(injects = EntryPoint.class) class TestModule {
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    graph = graph.plus(new ExampleLibraryModule());
    graph.validate();
  }

  @Test public void unusedSetBinding() throws Exception {
    @Module
    class TestModule {
      @Provides(type = Provides.Type.SET) String provideA() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void unusedSetValuesBinding() throws Exception {
    @Module
    class TestModule {
      @Provides(type = Provides.Type.SET_VALUES) Set<String> provideA() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.createWith(new TestingLoader(), new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
