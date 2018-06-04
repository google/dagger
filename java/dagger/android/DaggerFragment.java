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

package dagger.android;

import android.app.Fragment;
import android.content.Context;
import dagger.internal.Beta;
import javax.inject.Inject;

/**
 * A {@link Fragment} that injects its members in {@link #onAttach(Context)} and can be used to
 * inject child {@link Fragment}s attached to it. Note that when this fragment gets reattached, its
 * members will be injected again.
 *
 * @deprecated Framework fragments are deprecated in Android P; prefer {@code
 *     dagger.android.support.DaggerFragment} to use a support-library-friendly {@code
 *     dagger.android} fragment implementation.
 */
@Beta
@Deprecated
public abstract class DaggerFragment extends Fragment implements HasFragmentInjector {

  @Inject DispatchingAndroidInjector<Fragment> childFragmentInjector;

  @Override
  public void onAttach(Context context) {
    AndroidInjection.inject(this);
    super.onAttach(context);
  }

  @Override
  public AndroidInjector<Fragment> fragmentInjector() {
    return childFragmentInjector;
  }
}
