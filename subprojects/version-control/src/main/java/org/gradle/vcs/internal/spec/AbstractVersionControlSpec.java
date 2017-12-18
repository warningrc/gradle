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

package org.gradle.vcs.internal.spec;

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.vcs.VersionControlSpec;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

public abstract class AbstractVersionControlSpec implements VersionControlSpec {
    private String rootDir = "";
    private final CollectingPluginDependenciesSpec plugins = new CollectingPluginDependenciesSpec();

    @Override
    public String getRootDir() {
        return rootDir;
    }

    @Override
    public void setRootDir(String rootDir) {
        Preconditions.checkNotNull(rootDir, "rootDir should be non-null for '%s'.", getDisplayName());
        this.rootDir = rootDir;
    }

    @Override
    public void plugins(Action<? super PluginDependenciesSpec> configuration) {
        configuration.execute(plugins);
    }

    public PluginRequests getPluginRequests() {
        if (plugins.specs.isEmpty()) {
            return DefaultPluginRequests.EMPTY;
        } else {
            List<PluginRequestInternal> pluginRequests = collect(plugins.specs, new Transformer<PluginRequestInternal, RequestedPluginDependency>() {
                public PluginRequestInternal transform(RequestedPluginDependency original) {
                    // TODO:
                    return new DefaultPluginRequest(original.id, original.version, original.apply, null, "included build");
                }
            });

            return new DefaultPluginRequests(pluginRequests);
        }
    }

    private static class RequestedPluginDependency implements PluginDependencySpec {
        private final String id;
        private String version;
        private boolean apply;

        private RequestedPluginDependency(String id) {
            this.id = id;
            this.apply = true;
        }

        @Override
        public PluginDependencySpec version(@Nullable String version) {
            this.version = version;
            return this;
        }

        @Override
        public PluginDependencySpec apply(boolean apply) {
            this.apply = apply;
            return this;
        }
    }

    private static class CollectingPluginDependenciesSpec implements PluginDependenciesSpec {
        private final List<RequestedPluginDependency> specs = new LinkedList<RequestedPluginDependency>();

        @Override
        public PluginDependencySpec id(String id) {
            RequestedPluginDependency spec = new RequestedPluginDependency(id);
            specs.add(spec);
            return spec;
        }
    }
}
