/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_FACTORY;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.inject.Provider;

/**
 * A {@link Provider} creation expression for an {@link javax.inject.Inject @Inject}-constructed
 * class or a {@link dagger.Provides @Provides}-annotated module method.
 */
// TODO(dpb): Resolve with ProducerCreationExpression.
final class InjectionOrProvisionProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final ShardImplementation shardImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final XProcessingEnv processingEnv;

  @AssistedInject
  InjectionOrProvisionProviderCreationExpression(
      @Assisted ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      XProcessingEnv processingEnv) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.processingEnv = processingEnv;
  }

  @Override
  public CodeBlock creationExpression() {
    ClassName factoryImpl = generatedClassNameForBinding(binding);
    CodeBlock createFactory =
        CodeBlock.of(
            "$T.create($L)",
            factoryImpl,
            componentRequestRepresentations.getCreateMethodArgumentsCodeBlock(
                binding, shardImplementation.name()));

    // If this is for an AssistedFactory, then we may need to wrap the call in case we're building
    // against a library built at an older version of Dagger before the changes to make factories
    // return a Dagger Provider instead of a javax.inject.Provider.
    if (binding.kind().equals(ASSISTED_FACTORY)) {
      XTypeElement factoryType = processingEnv.findTypeElement(factoryImpl);
      // If we can't find the factory, then assume it is being generated this run, which means
      // it should be the newer version and not need wrapping. If it is missing for some other
      // reason, then that likely means there will just be some other compilation failure.
      if (factoryType != null) {
        XMethodElement createMethod = factoryType.getDeclaredMethods().stream()
            .filter(method -> method.isStatic() && getSimpleName(method).equals("create"))
            .collect(onlyElement());
        // Only convert it if it returns the older javax.inject.Provider type.
        if (createMethod.getReturnType().getRawType().getTypeName().equals(TypeNames.PROVIDER)) {
          createFactory = CodeBlock.of(
              "$T.asDaggerProvider($L)", TypeNames.DAGGER_PROVIDERS, createFactory);
        }
      }
    }

    // When scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
    // type properly, so cast to a raw framework type before scoping.
    if (binding.kind().equals(INJECTION)
        && binding.unresolved().isPresent()
        && binding.scope().isPresent()) {
      return CodeBlocks.cast(createFactory, TypeNames.DAGGER_PROVIDER);
    } else {
      return createFactory;
    }
  }

  @AssistedFactory
  static interface Factory {
    InjectionOrProvisionProviderCreationExpression create(ContributionBinding binding);
  }
}
