package coffee;

import dagger.Component;

@Component(modules = DripCoffeeModule.class)
public interface CoffeeApp {

  CoffeeMaker pullOffTheShelf();

}
