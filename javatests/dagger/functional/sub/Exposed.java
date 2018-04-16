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

import dagger.Lazy;
import dagger.functional.Generic;
import dagger.functional.Generic2;
import javax.inject.Inject;
import javax.inject.Provider;

public class Exposed {

  @Inject public PackagePrivate pp2;
  @Inject public Provider<PackagePrivate> ppp2;
  @Inject public Lazy<PackagePrivate> lpp2;
  @Inject public Provider<Lazy<PackagePrivate>> plpp2;
  @Inject public Generic2<PackagePrivate> gpp2;
  @Inject public Generic2<PackagePrivateContainer.PublicEnclosed> gppc2;
  @Inject public Provider<Generic2<PackagePrivate>> pgpp2;
  @Inject public Lazy<Generic2<PackagePrivate>> lgpp2;
  @Inject public Provider<Lazy<Generic2<PackagePrivate>>> plgpp2;

  public PackagePrivate pp;
  public Provider<PackagePrivate> ppp;
  public Lazy<PackagePrivate> lpp;
  public Provider<Lazy<PackagePrivate>> plpp;
  public Generic<PackagePrivate> gpp;
  public Generic<PackagePrivateContainer.PublicEnclosed> gppc;
  public Provider<Generic<PackagePrivate>> pgpp;
  public Lazy<Generic<PackagePrivate>> lgpp;
  public Provider<Lazy<Generic<PackagePrivate>>> plgpp;

  /** Injects inaccessible dependencies to test casting of these dependency arguments. */
  @Inject Exposed(
      PackagePrivate pp,
      Provider<PackagePrivate> ppp,
      Lazy<PackagePrivate> lpp,
      Provider<Lazy<PackagePrivate>> plpp,
      Generic<PackagePrivate> gpp,
      Generic<PackagePrivateContainer.PublicEnclosed> gppc,
      Provider<Generic<PackagePrivate>> pgpp,
      Lazy<Generic<PackagePrivate>> lgpp,
      Provider<Lazy<Generic<PackagePrivate>>> plgpp) {
    this.pp = pp;
    this.ppp = ppp;
    this.lpp = lpp;
    this.plpp = plpp;
    this.gpp = gpp;
    this.gppc = gppc;
    this.pgpp = pgpp;
    this.lgpp = lgpp;
    this.plgpp = plgpp;
  }
}
