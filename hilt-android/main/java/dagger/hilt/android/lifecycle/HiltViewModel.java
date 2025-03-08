/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.android.lifecycle;

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a {@link androidx.lifecycle.ViewModel} for construction injection.
 *
 * <p>The {@code ViewModel} annotated with {@link HiltViewModel} will be available for creation by
 * the {@link dagger.hilt.android.lifecycle.HiltViewModelFactory} and can be retrieved by default in
 * an {@code Activity} or {@code Fragment} annotated with {@link
 * dagger.hilt.android.AndroidEntryPoint}. The {@code HiltViewModel} containing a constructor
 * annotated with {@link javax.inject.Inject} will have its dependencies defined in the constructor
 * parameters injected by Dagger's Hilt.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;HiltViewModel
 * public class DonutViewModel extends ViewModel {
 *     &#64;Inject
 *     public DonutViewModel(SavedStateHandle handle, RecipeRepository repository) {
 *         // ...
 *     }
 * }
 * </pre>
 *
 * <pre>
 * &#64;AndroidEntryPoint
 * public class CookingActivity extends AppCompatActivity {
 *     public void onCreate(Bundle savedInstanceState) {
 *         DonutViewModel vm = new ViewModelProvider(this).get(DonutViewModel.class);
 *     }
 * }
 * </pre>
 *
 * <p>{@code ViewModel}s annotated with {@link HiltViewModel} can also be used with assisted
 * injection:
 *
 * <pre>
 * &#64;HiltViewModel(assistedFactory = DonutViewModel.Factory.class)
 * public class DonutViewModel extends ViewModel {
 *     &#64;AssistedInject
 *     public DonutViewModel(
 *         SavedStateHandle handle,
 *         RecipeRepository repository, 
 *         $#64;Assisted int donutId
 *     ) {
 *         // ...
 *     }
 *
 *     &#64;AssistedFactory
 *     public interface Factory {
 *         DonutViewModel create(int donutId);
 *     }
 * }
 * </pre>
 *
 * <pre>
 * &#64;AndroidEntryPoint
 * public class CookingActivity extends AppCompatActivity {
 *     public void onCreate(Bundle savedInstanceState) {
 *         DonutViewModel vm = new ViewModelProvider(
 *             getViewModelStore(),
 *             getDefaultViewModelProviderFactory(),
 *             HiltViewModelExtensions.withCreationCallback(
 *                 getDefaultViewModelCreationExtras(),
 *                 (DonutViewModel.Factory factory) -> factory.create(1)
 *             )
 *         ).get(DonutViewModel.class);
 *     }
 * }
 * </pre>
 *
 * <p>Exactly one constructor in the {@code ViewModel} must be annotated with {@code Inject} or
 * {@code AssistedInject}.
 *
 * <p>Only dependencies available in the {@link dagger.hilt.android.components.ViewModelComponent}
 * can be injected into the {@code ViewModel}.
 *
 * <p>
 *
 * @see dagger.hilt.android.components.ViewModelComponent
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@GeneratesRootInput
public @interface HiltViewModel {
  /**
   * Returns a factory class that can be used to create this ViewModel with assisted injection. The
   * default value `Object.class` denotes that no factory is specified and the ViewModel is not
   * assisted injected.
   */
  Class<?> assistedFactory() default Object.class;
}
