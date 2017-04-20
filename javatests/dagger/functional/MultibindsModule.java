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

package dagger.functional;

import dagger.Module;
import dagger.multibindings.Multibinds;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;

/**
 * A module that uses {@link Multibinds @Multibinds}-annotated abstract methods to declare
 * multibindings.
 */
@Module
abstract class MultibindsModule {

  @Multibinds
  abstract Set<Object> emptySet();

  @Multibinds
  abstract Map<String, Object> emptyMap();

  @Multibinds
  abstract Set<CharSequence> set();

  @Multibinds
  abstract Map<String, CharSequence> map();

  @Multibinds
  @Named("complexQualifier")
  abstract Set<Object> emptyQualifiedSet();

  @Multibinds
  @Named("complexQualifier")
  abstract Map<String, Object> emptyQualifiedMap();

  @Multibinds
  @Named("complexQualifier")
  abstract Set<CharSequence> qualifiedSet();

  @Multibinds
  @Named("complexQualifier")
  abstract Map<String, CharSequence> qualifiedMap();
}
