/*
 * Copyright (C) 2015 The Dagger Authors.
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

import dagger.MembersInjector;

/** Whether a binding or declaration is for provision, production, or a {@link MembersInjector}. */
enum BindingType {
  /** A binding with this type is a {@link ProvisionBinding}. */
  PROVISION,

  /** A binding with this type is a {@link MembersInjectionBinding}. */
  MEMBERS_INJECTION,

  /** A binding with this type is a {@link ProductionBinding}. */
  PRODUCTION,
}
