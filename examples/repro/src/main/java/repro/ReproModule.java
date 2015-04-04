package repro;

import dagger.Module;
import dagger.Provides;

@Module
public class ReproModule {
  @Provides
  String theValue() {
    return "";
  }
}
