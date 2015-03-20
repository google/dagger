package coffee;

import dagger.Component;
import javax.inject.Singleton;

public class CoffeeApp {
  @Singleton
  @Component(modules = { DripCoffeeModule.class, PumpModule.class })
  public interface Coffee {
    CoffeeMaker maker();
  }

  public static void main(String[] args) {
    Coffee coffee = Dagger_CoffeeApp_Coffee.builder().build();
    coffee.maker().brew();
  }
}
