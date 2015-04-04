package repro;

import dagger.Component;

@Component(modules = ReproModule.class)
public interface ReproComponent {
  String value();
}
