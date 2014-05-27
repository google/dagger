package dagger.internal.codegen;

import dagger.Module;
import dagger.Provides;

@Module(library = true)
public class TestModule {
  @Provides String string() { return ""; }
}
