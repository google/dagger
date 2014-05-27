package coffee;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module(
    complete = false,
    library = true,
    injects = CoffeeApp.class,
    includes = PumpModule.class
)
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
}
