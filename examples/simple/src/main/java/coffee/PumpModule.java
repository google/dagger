package coffee;

import dagger2.Module;
import dagger2.Provides;

@Module
class PumpModule {
  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
