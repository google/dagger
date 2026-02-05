package dagger.example.android.espresso.stubbed;

import dagger.Module;
import dagger.Provides;
import dagger.example.android.espresso.main.ApiClient;

@Module
public class StubNetworkingModule {
  @Provides
  ApiClient provideApiClient() {
    return new ApiClient() {
      @Override
      public String getRemoteResource() {
        return "stub remote resource";
      }
    };
  }
}
