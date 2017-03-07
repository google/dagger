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

package dagger.android.support.functional;

import android.content.Intent;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import dagger.android.DaggerService;
import java.io.FileDescriptor;
import java.util.Set;
import javax.inject.Inject;

public final class TestService extends DaggerService {
  @Inject Set<Class<?>> componentHierarchy;

  @Override
  public IBinder onBind(Intent intent) {
    return new MockBinder();
  }

  private static class MockBinder implements IBinder {
    @Override
    public String getInterfaceDescriptor() throws RemoteException {
      return null;
    }

    @Override
    public boolean pingBinder() {
      return false;
    }

    @Override
    public boolean isBinderAlive() {
      return false;
    }

    @Override
    public IInterface queryLocalInterface(String descriptor) {
      return null;
    }

    @Override
    public void dump(FileDescriptor fd, String[] args) throws RemoteException {}

    @Override
    public void dumpAsync(FileDescriptor fd, String[] args) throws RemoteException {}

    @Override
    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
      return false;
    }

    @Override
    public void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException {}

    @Override
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
      return false;
    }
  }
}
