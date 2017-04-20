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

package dagger.functional.sub;

import dagger.functional.Generic;
import dagger.functional.Generic2;
import javax.inject.Inject;

public class Exposed {
  
  @Inject public Generic2<PackagePrivate> gpp2;
  @Inject public Generic2<PackagePrivateContainer.PublicEnclosed> gppc2;

  public Generic<PackagePrivate> gpp;
  public Generic<PackagePrivateContainer.PublicEnclosed> gppc;
  
  @Inject Exposed(Generic<PackagePrivate> gpp, Generic<PackagePrivateContainer.PublicEnclosed> gppc) {
    this.gpp = gpp;
    this.gppc = gppc;
  }
}
