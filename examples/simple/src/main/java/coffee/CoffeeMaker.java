package coffee;

import dagger.Lazy;
import javax.inject.Inject;

class CoffeeMaker {
  @Inject Lazy<Heater> heater; // Don't want to create a possibly costly heater until we need it.
  @Inject Pump pump;
  @Inject CoffeeMaker() {}

  public void brew() {
    heater.get().on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.get().off();
  }
}
