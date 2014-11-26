package coffee;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
}
