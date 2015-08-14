package test;

import dagger2.Component;
import javax.inject.Singleton;

@Singleton
@Component
interface SingletonGenericComponent {
  
  ScopedGeneric<A> scopedGenericA();
  ScopedGeneric<B> scopedGenericB();

}
