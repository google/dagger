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

import com.google.auto.value.AutoValue;
import dagger.model.Key;

/**
 * A value object that pairs a {@link Key} with the style of its binding (i.e., whether it's a
 * members injector or normal contribution).
 *
 *  @author Gregory Kick
 *  @since 2.0
 */
@AutoValue
abstract class BindingKey {
  /** The style of binding that makes a {@link Key} available. */
  enum Kind {
    CONTRIBUTION, MEMBERS_INJECTION;
  }

  static BindingKey contribution(Key key) {
    return new AutoValue_BindingKey(Kind.CONTRIBUTION, key);
  }

  static BindingKey membersInjection(Key key) {
    return new AutoValue_BindingKey(Kind.MEMBERS_INJECTION, key);
  }

  abstract Kind kind();
  abstract Key key();
}
