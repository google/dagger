/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.builderbinds;

import dagger.BindsInstance;
import dagger.Component;
import java.util.List;
import javax.inject.Named;

@Component
interface TestComponent {
  int count();

  long l();

  @Named("input")
  String input();

  @Nullable
  @Named("nullable input")
  String nullableInput();

  List<String> listOfString();

  @Named("subtype")
  int boundInSubtype();

  @Component.Builder
  interface Builder extends BuilderSupertype {
    @BindsInstance
    Builder count(int count);

    @BindsInstance
    Builder l(long l);

    @BindsInstance
    Builder input(@Named("input") String input);

    @BindsInstance
    Builder nullableInput(@Nullable @Named("nullable input") String nullableInput);

    @BindsInstance
    Builder listOfString(List<String> listOfString);

    TestComponent build();
  }
}
