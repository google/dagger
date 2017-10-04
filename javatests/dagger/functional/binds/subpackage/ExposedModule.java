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

package dagger.functional.binds.subpackage;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Module
public abstract class ExposedModule {
  @Binds
  abstract Exposed notExposed(NotExposed notExposed);

  @Provides
  @Singleton // force a rawtypes Provider
  static List<NotExposed> notExposedList() {
    return new ArrayList<>();
  }

  @Binds
  abstract List<? extends Exposed> bindList(List<NotExposed> notExposedList);

  @Binds
  abstract ExposedInjectsMembers bindExposedInjectsMembers(
      NotExposedInjectsMembers notExposedInjectsMembers);
}
