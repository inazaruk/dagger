/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.writing.DelegateBindingExpression.isBindsScopeStrongerThanDependencyScope;
import static dagger.internal.codegen.writing.MemberSelect.staticFactoryCreation;
import static dagger.spi.model.BindingKind.DELEGATE;
import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.spi.model.BindingKind;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.Optional;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class LegacyBindingRepresentation implements BindingRepresentation {
  private final BindingGraph graph;
  private final boolean isFastInit;
  private final Binding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentMethodBindingExpression.Factory componentMethodBindingExpressionFactory;
  private final DelegateBindingExpression.Factory delegateBindingExpressionFactory;
  private final DerivedFromFrameworkInstanceBindingExpression.Factory
      derivedFromFrameworkInstanceBindingExpressionFactory;
  private final ImmediateFutureBindingExpression.Factory immediateFutureBindingExpressionFactory;
  private final MembersInjectionBindingExpression.Factory membersInjectionBindingExpressionFactory;
  private final PrivateMethodBindingExpression.Factory privateMethodBindingExpressionFactory;
  private final AssistedPrivateMethodBindingExpression.Factory
      assistedPrivateMethodBindingExpressionFactory;
  private final ProducerNodeInstanceBindingExpression.Factory
      producerNodeInstanceBindingExpressionFactory;
  private final ProviderInstanceBindingExpression.Factory providerInstanceBindingExpressionFactory;
  private final UnscopedDirectInstanceBindingExpressionFactory
      unscopedDirectInstanceBindingExpressionFactory;
  private final ProducerFromProviderCreationExpression.Factory
      producerFromProviderCreationExpressionFactory;
  private final UnscopedFrameworkInstanceCreationExpressionFactory
      unscopedFrameworkInstanceCreationExpressionFactory;
  private final SwitchingProviders switchingProviders;

  @AssistedInject
  LegacyBindingRepresentation(
      @Assisted boolean isFastInit,
      @Assisted Binding binding,
      @Assisted SwitchingProviders switchingProviders,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentMethodBindingExpression.Factory componentMethodBindingExpressionFactory,
      DelegateBindingExpression.Factory delegateBindingExpressionFactory,
      DerivedFromFrameworkInstanceBindingExpression.Factory
          derivedFromFrameworkInstanceBindingExpressionFactory,
      ImmediateFutureBindingExpression.Factory immediateFutureBindingExpressionFactory,
      MembersInjectionBindingExpression.Factory membersInjectionBindingExpressionFactory,
      PrivateMethodBindingExpression.Factory privateMethodBindingExpressionFactory,
      AssistedPrivateMethodBindingExpression.Factory assistedPrivateMethodBindingExpressionFactory,
      ProducerNodeInstanceBindingExpression.Factory producerNodeInstanceBindingExpressionFactory,
      ProviderInstanceBindingExpression.Factory providerInstanceBindingExpressionFactory,
      UnscopedDirectInstanceBindingExpressionFactory unscopedDirectInstanceBindingExpressionFactory,
      ProducerFromProviderCreationExpression.Factory producerFromProviderCreationExpressionFactory,
      UnscopedFrameworkInstanceCreationExpressionFactory
          unscopedFrameworkInstanceCreationExpressionFactory,
      DaggerTypes types) {
    this.isFastInit = isFastInit;
    this.binding = binding;
    this.switchingProviders = switchingProviders;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.componentMethodBindingExpressionFactory = componentMethodBindingExpressionFactory;
    this.delegateBindingExpressionFactory = delegateBindingExpressionFactory;
    this.derivedFromFrameworkInstanceBindingExpressionFactory =
        derivedFromFrameworkInstanceBindingExpressionFactory;
    this.immediateFutureBindingExpressionFactory = immediateFutureBindingExpressionFactory;
    this.membersInjectionBindingExpressionFactory = membersInjectionBindingExpressionFactory;
    this.privateMethodBindingExpressionFactory = privateMethodBindingExpressionFactory;
    this.producerNodeInstanceBindingExpressionFactory =
        producerNodeInstanceBindingExpressionFactory;
    this.providerInstanceBindingExpressionFactory = providerInstanceBindingExpressionFactory;
    this.unscopedDirectInstanceBindingExpressionFactory =
        unscopedDirectInstanceBindingExpressionFactory;
    this.producerFromProviderCreationExpressionFactory =
        producerFromProviderCreationExpressionFactory;
    this.unscopedFrameworkInstanceCreationExpressionFactory =
        unscopedFrameworkInstanceCreationExpressionFactory;
    this.assistedPrivateMethodBindingExpressionFactory =
        assistedPrivateMethodBindingExpressionFactory;
  }

  @Override
  public BindingExpression getBindingExpression(BindingRequest request) {
    switch (binding.bindingType()) {
      case MEMBERS_INJECTION:
        checkArgument(request.isRequestKind(RequestKind.MEMBERS_INJECTION));
        return membersInjectionBindingExpressionFactory.create((MembersInjectionBinding) binding);

      case PROVISION:
        return provisionBindingExpression((ContributionBinding) binding, request);

      case PRODUCTION:
        return productionBindingExpression((ContributionBinding) binding, request);
    }
    throw new AssertionError(binding);
  }

  /**
   * Returns a binding expression that uses a {@link javax.inject.Provider} for provision bindings
   * or a {@link dagger.producers.Producer} for production bindings.
   */
  private BindingExpression frameworkInstanceBindingExpression(ContributionBinding binding) {
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        unscopedFrameworkInstanceCreationExpressionFactory.create(binding);

    if (isFastInit
        // Some creation expressions can opt out of using switching providers.
        && frameworkInstanceCreationExpression.useSwitchingProvider()
        // Production types are not yet supported with switching providers.
        && binding.bindingType() != BindingType.PRODUCTION) {
      // First try to get the instance expression via getBindingExpression(). However, if that
      // expression is a DerivedFromFrameworkInstanceBindingExpression (e.g. fooProvider.get()),
      // then we can't use it to create an instance within the SwitchingProvider since that would
      // cause a cycle. In such cases, we try to use the unscopedDirectInstanceBindingExpression
      // directly, or else fall back to default mode.
      BindingRequest instanceRequest = bindingRequest(binding.key(), RequestKind.INSTANCE);
      BindingExpression instanceExpression = getBindingExpression(instanceRequest);
      if (!(instanceExpression instanceof DerivedFromFrameworkInstanceBindingExpression)) {
        frameworkInstanceCreationExpression =
            switchingProviders.newFrameworkInstanceCreationExpression(binding, instanceExpression);
      } else {
        Optional<BindingExpression> unscopedInstanceExpression =
            unscopedDirectInstanceBindingExpressionFactory.create(binding);
        if (unscopedInstanceExpression.isPresent()) {
          frameworkInstanceCreationExpression =
              switchingProviders.newFrameworkInstanceCreationExpression(
                  binding,
                  unscopedInstanceExpression.get().requiresMethodEncapsulation()
                      ? privateMethodBindingExpressionFactory.create(
                          instanceRequest, binding, unscopedInstanceExpression.get())
                      : unscopedInstanceExpression.get());
        }
      }
    }

    // TODO(bcorso): Consider merging the static factory creation logic into CreationExpressions?
    Optional<MemberSelect> staticMethod =
        useStaticFactoryCreation() ? staticFactoryCreation(binding) : Optional.empty();
    FrameworkInstanceSupplier frameworkInstanceSupplier =
        staticMethod.isPresent()
            ? staticMethod::get
            : new FrameworkFieldInitializer(
                componentImplementation,
                binding,
                binding.scope().isPresent()
                    ? scope(frameworkInstanceCreationExpression)
                    : frameworkInstanceCreationExpression);

    switch (binding.bindingType()) {
      case PROVISION:
        return providerInstanceBindingExpressionFactory.create(binding, frameworkInstanceSupplier);
      case PRODUCTION:
        return producerNodeInstanceBindingExpressionFactory.create(
            binding, frameworkInstanceSupplier);
      default:
        throw new AssertionError("invalid binding type: " + binding.bindingType());
    }
  }

  private FrameworkInstanceCreationExpression scope(FrameworkInstanceCreationExpression unscoped) {
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            binding.scope().get().isReusable() ? SINGLE_CHECK : DOUBLE_CHECK,
            unscoped.creationExpression());
  }

  /** Returns a binding expression for a provision binding. */
  private BindingExpression provisionBindingExpression(
      ContributionBinding binding, BindingRequest request) {
    Key key = request.key();
    switch (request.requestKind()) {
      case INSTANCE:
        return instanceBindingExpression(binding);

      case PROVIDER:
        return providerBindingExpression(binding);

      case LAZY:
      case PRODUCED:
      case PROVIDER_OF_LAZY:
        return derivedFromFrameworkInstanceBindingExpressionFactory.create(
            request, FrameworkType.PROVIDER);

      case PRODUCER:
        return producerFromProviderBindingExpression(binding);

      case FUTURE:
        return immediateFutureBindingExpressionFactory.create(key);

      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
    }

    throw new AssertionError();
  }

  /** Returns a binding expression for a production binding. */
  private BindingExpression productionBindingExpression(
      ContributionBinding binding, BindingRequest request) {
    return request.frameworkType().isPresent()
        ? frameworkInstanceBindingExpression(binding)
        : derivedFromFrameworkInstanceBindingExpressionFactory.create(
            request, FrameworkType.PRODUCER_NODE);
  }

  /**
   * Returns a binding expression for {@link RequestKind#PROVIDER} requests.
   *
   * <p>{@code @Binds} bindings that don't {@linkplain #needsCaching(ContributionBinding) need to be
   * cached} can use a {@link DelegateBindingExpression}.
   *
   * <p>Otherwise, return a {@link FrameworkInstanceBindingExpression}.
   */
  private BindingExpression providerBindingExpression(ContributionBinding binding) {
    if (binding.kind().equals(DELEGATE) && !needsCaching(binding)) {
      return delegateBindingExpressionFactory.create(binding, RequestKind.PROVIDER);
    }
    return frameworkInstanceBindingExpression(binding);
  }

  /**
   * Returns a binding expression that uses a {@link dagger.producers.Producer} field for a
   * provision binding.
   */
  private FrameworkInstanceBindingExpression producerFromProviderBindingExpression(
      ContributionBinding binding) {
    checkArgument(binding.bindingType().equals(BindingType.PROVISION));
    return producerNodeInstanceBindingExpressionFactory.create(
        binding,
        new FrameworkFieldInitializer(
            componentImplementation,
            binding,
            producerFromProviderCreationExpressionFactory.create(binding)));
  }

  /** Returns a binding expression for {@link RequestKind#INSTANCE} requests. */
  private BindingExpression instanceBindingExpression(ContributionBinding binding) {
    Optional<BindingExpression> maybeDirectInstanceExpression =
        unscopedDirectInstanceBindingExpressionFactory.create(binding);
    if (maybeDirectInstanceExpression.isPresent()) {
      BindingExpression directInstanceExpression = maybeDirectInstanceExpression.get();
      if (binding.kind() == BindingKind.ASSISTED_INJECTION) {
        BindingRequest request = bindingRequest(binding.key(), RequestKind.INSTANCE);
        return assistedPrivateMethodBindingExpressionFactory.create(
            request, binding, directInstanceExpression);
      }

      boolean isDefaultModeAssistedFactory =
          binding.kind() == BindingKind.ASSISTED_FACTORY && !isFastInit;

      // If this is the case where we don't need to use Provider#get() because there's no caching
      // and it isn't a default mode assisted factory, we can try to use the direct expression,
      // possibly wrapped in a method if necessary (e.g. if it has dependencies).
      // Note: We choose not to use a direct expression for assisted factories in default mode
      // because they technically act more similar to a Provider than an instance, so we cache them
      // using a field in the component similar to Provider requests. This should also be the case
      // in FastInit, but it hasn't been implemented yet.
      if (!needsCaching(binding) && !isDefaultModeAssistedFactory) {
        return directInstanceExpression.requiresMethodEncapsulation()
            ? wrapInMethod(binding, RequestKind.INSTANCE, directInstanceExpression)
            : directInstanceExpression;
      }
    }
    return derivedFromFrameworkInstanceBindingExpressionFactory.create(
        bindingRequest(binding.key(), RequestKind.INSTANCE), FrameworkType.PROVIDER);
  }

  /**
   * Returns {@code true} if the binding should use the static factory creation strategy.
   *
   * <p>In default mode, we always use the static factory creation strategy. In fastInit mode, we
   * prefer to use a SwitchingProvider instead of static factories in order to reduce class loading;
   * however, we allow static factories that can reused across multiple bindings, e.g. {@code
   * MapFactory} or {@code SetFactory}.
   */
  private boolean useStaticFactoryCreation() {
    return !isFastInit
        || binding.kind().equals(MULTIBOUND_MAP)
        || binding.kind().equals(MULTIBOUND_SET);
  }

  /**
   * Returns a binding expression that uses a given one as the body of a method that users call. If
   * a component provision method matches it, it will be the method implemented. If it does not
   * match a component provision method and the binding is modifiable, then a new public modifiable
   * binding method will be written. If the binding doesn't match a component method and is not
   * modifiable, then a new private method will be written.
   */
  BindingExpression wrapInMethod(
      ContributionBinding binding, RequestKind requestKind, BindingExpression bindingExpression) {
    // If we've already wrapped the expression, then use the delegate.
    if (bindingExpression instanceof MethodBindingExpression) {
      return bindingExpression;
    }

    BindingRequest request = bindingRequest(binding.key(), requestKind);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.componentDescriptor().firstMatchingComponentMethod(request);

    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);

    // Consider the case of a request from a component method like:
    //
    //   DaggerMyComponent extends MyComponent {
    //     @Overrides
    //     Foo getFoo() {
    //       <FOO_BINDING_REQUEST>
    //     }
    //   }
    //
    // Normally, in this case we would return a ComponentMethodBindingExpression rather than a
    // PrivateMethodBindingExpression so that #getFoo() can inline the implementation rather than
    // create an unnecessary private method and return that. However, with sharding we don't want to
    // inline the implementation because that would defeat some of the class pool savings if those
    // fields had to communicate across shards. Thus, when a key belongs to a separate shard use a
    // PrivateMethodBindingExpression and put the private method in the shard.
    if (matchingComponentMethod.isPresent() && shardImplementation.isComponentShard()) {
      ComponentMethodDescriptor componentMethod = matchingComponentMethod.get();
      return componentMethodBindingExpressionFactory.create(bindingExpression, componentMethod);
    } else {
      return privateMethodBindingExpressionFactory.create(request, binding, bindingExpression);
    }
  }

  /**
   * Returns {@code true} if the component needs to make sure the provided value is cached.
   *
   * <p>The component needs to cache the value for scoped bindings except for {@code @Binds}
   * bindings whose scope is no stronger than their delegate's.
   */
  private boolean needsCaching(ContributionBinding binding) {
    if (!binding.scope().isPresent()) {
      return false;
    }
    if (binding.kind().equals(DELEGATE)) {
      return isBindsScopeStrongerThanDependencyScope(binding, graph);
    }
    return true;
  }

  @AssistedFactory
  static interface Factory {
    LegacyBindingRepresentation create(
        boolean isFastInit, Binding binding, SwitchingProviders switchingProviders);
  }
}
