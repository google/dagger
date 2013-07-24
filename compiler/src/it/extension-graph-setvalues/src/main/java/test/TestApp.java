/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import dagger.ObjectGraph;
import dagger.Module;
import dagger.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;

/**
 * Contributions to {@code SET_VALUES} binding do not affect Set of providers.
 */
class TestApp implements Runnable {
  @Inject Set<Provider<String>> providers;
  @Inject Set<String> strings;

  @Override public void run() {
    System.out.println(strings);
  }

  public static void main(String[] args) {
    ObjectGraph root = ObjectGraph.create(new RootModule());
    ObjectGraph extension = root.plus(new ExtensionModule());
    extension.get(TestApp.class).run();
  }
  
  @Module(injects = TestApp.class)
  static class RootModule {
    @Provides Set<Provider<String>> providers() {
      return new HashSet<Provider<String>>();
    }
    @Provides(type = SET_VALUES) Set<String> strings() {
      return new HashSet<String>();
    }
  }

  @Module(addsTo = RootModule.class, injects = TestApp.class)
  static class ExtensionModule {
    @Provides(type = SET) String addToSet() {
      return "contributed";
    }
  }
}
