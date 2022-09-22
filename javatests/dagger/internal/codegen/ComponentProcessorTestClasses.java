/*
 * Copyright (C) 2022 The Dagger Authors.
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

import javax.inject.Inject;

/**
 * Test classes for {@link ComponentProcessorTest}.
 *
 * <p>These classes live in a separate library to test the case where the Dagger compiler is not run
 * during compilation.
 */
public final class ComponentProcessorTestClasses {
  public static final class NoInjectMemberNoConstructor {}

  public static final class NoInjectMemberWithConstructor {
     @Inject
     NoInjectMemberWithConstructor() {}
  }

  public abstract static class LocalInjectMemberNoConstructor {
    @Inject Object object;
  }

  public static final class LocalInjectMemberWithConstructor {
    @SuppressWarnings("BadInject") // Ignore this check as we want to test this case in particular.
    @Inject Object object;

    @Inject
    LocalInjectMemberWithConstructor() {}
  }

  public static final class ParentInjectMemberNoConstructor extends LocalInjectMemberNoConstructor {
  }

  public static final class ParentInjectMemberWithConstructor
      extends LocalInjectMemberNoConstructor {
    @Inject
    ParentInjectMemberWithConstructor() {}
  }

  private ComponentProcessorTestClasses() {}
}
