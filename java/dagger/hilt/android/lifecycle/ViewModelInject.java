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
 * Identifies a {@link androidx.lifecycle.ViewModel}'s constructor for injection.
 *
 * <p>Similar to {@link javax.inject.Inject}, a {@code ViewModel} containing a constructor annotated
 * with {@code ViewModelInject} will have its dependencies defined in the constructor parameters
 * injected by Dagger's Hilt. The {@code ViewModel} will be available for creation by the {@link
 * dagger.hilt.android.lifecycle.HiltViewModelFactory} and can be retrieved by default in an {@code
 * Activity} or {@code Fragment} annotated with {@link dagger.hilt.android.AndroidEntryPoint}.
 *
 * <p>Example:
 *
 * <pre>
 * public class DonutViewModel extends ViewModel {
 *     &#64;ViewModelInject
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
 * <p>Only one constructor in the {@code ViewModel} must be annotated with {@code ViewModelInject}.
 *
 * <p>Only dependencies available in the {@link dagger.hilt.android.components.ViewModelComponent}
 * can be injected into the {@code ViewModel}.
 *
 * <p>
 *
 * @see dagger.hilt.android.components.ViewModelComponent
 */
@Target(ElementType.CONSTRUCTOR)
@Retention(RetentionPolicy.CLASS)
@GeneratesRootInput
public @interface ViewModelInject {}
