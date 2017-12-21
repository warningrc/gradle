/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.PluginResolverFactory;
import org.gradle.plugin.use.internal.RootBuildPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

public class DefaultRootBuildPluginResolver implements RootBuildPluginResolver {
    private PluginResolver rootBuildResolver;

    @Override
    public void storeRootResolver(ServiceRegistry serviceRegistry, ClassLoaderScope classLoaderScope) {
        PluginResolverFactory factory = serviceRegistry.get(PluginResolverFactory.class);
        this.rootBuildResolver = factory.create(classLoaderScope, null);
    }

    public PluginResolver getRootBuildResolver() {
        return rootBuildResolver;
    }
}
