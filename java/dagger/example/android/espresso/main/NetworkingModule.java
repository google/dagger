package dagger.example.android.espresso.main;

import dagger.Module;
import dagger.Provides;

@Module
public class NetworkingModule {
  @Provides ApiClient provideApiClient() {
    return new ApiClient() {
      @Override
      public String getRemoteResource() {
        return "remote resource";
      }
    };
  }
}
