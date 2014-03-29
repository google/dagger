package dagger.internal;

import dagger.MembersInjector;

/**
 * A {@link MembersInjector} implementation that injects no members; a valid null object for types
 * that have no members (or inherited members) annotated with {@link javax.inject.Inject}.
 *
 * @since 2.0
 */
public final class NoOpMembersInjector<T> implements MembersInjector<T> {
  @SuppressWarnings("unchecked")
  public <T> MembersInjector<T> create() {
    // this cast is safe because this members injector is a no-op
    return (MembersInjector<T>) INSTANCE;
  }

  private static final NoOpMembersInjector<Object> INSTANCE = new NoOpMembersInjector<Object>() ;

  @Override
  public void injectMembers(T instance) {}

  private NoOpMembersInjector () {}
}
