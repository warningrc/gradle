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

package org.gradle.vcs.internal;

import com.google.common.io.CharSink;
import com.google.common.io.Files;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.initialization.Settings;
import org.gradle.api.resources.TextResource;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CleanupActionFactory;
import org.gradle.cache.internal.FixedAgeOldestCacheCleanup;
import org.gradle.internal.Factory;
import org.gradle.internal.IoActions;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.util.GFileUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.git.internal.GitVersionControlSystem;
import org.gradle.vcs.internal.spec.DirectoryRepositorySpec;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultVersionControlSystemFactory implements VersionControlSystemFactory, Stoppable {
    private final PersistentCache vcsWorkingDirCache;

    DefaultVersionControlSystemFactory(VcsWorkingDirectoryRoot workingDirectoryRoot, CacheRepository cacheRepository, CleanupActionFactory cleanupActionFactory) {
        this.vcsWorkingDirCache = cacheRepository
            .cache(workingDirectoryRoot.getDir())
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withDisplayName("VCS Checkout Cache")
            .withCleanup(cleanupActionFactory.create(new FixedAgeOldestCacheCleanup(7L)))
            .open();
    }

    @Override
    public VersionControlSystem create(VersionControlSpec spec) {
        // TODO: Register these mappings somewhere
        VersionControlSystem vcs;
        if (spec instanceof DirectoryRepositorySpec) {
            vcs = new SimpleVersionControlSystem();
        } else {
            vcs = new GitVersionControlSystem();
        }
        return new LockingVersionControlSystem(
            new SettingsGeneratingVersionControlSystem(
                vcs
            ),
            vcsWorkingDirCache
        );
    }

    @Override
    public void stop() {
        vcsWorkingDirCache.close();
    }

    private static final class SettingsGeneratingVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;

        private SettingsGeneratingVersionControlSystem(VersionControlSystem delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
            return delegate.getAvailableVersions(spec);
        }

        @Override
        public File populate(final File versionDir, final VersionRef ref, final VersionControlSpec spec) {
            final File workingDir = new File(delegate.populate(versionDir, ref, spec), spec.getRootDir());

            TextResource textResource = spec.getSettingsFile();
            if (textResource != null) {
                final Reader userProvidedSettings = textResource.asReader();
                IoActions.withResource(userProvidedSettings, new Action<Reader>() {
                    @Override
                    public void execute(Reader reader) {
                        // TODO: Make this configurable
                        File targetSettings = new File(workingDir, Settings.DEFAULT_SETTINGS_FILE);
                        try {
                            CharSink writer = Files.asCharSink(targetSettings, Charset.defaultCharset());
                            writer.writeFrom(userProvidedSettings);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }

            return workingDir;
        }
    }

    private static final class LockingVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;
        private final CacheAccess cacheAccess;

        private LockingVersionControlSystem(VersionControlSystem delegate, CacheAccess cacheAccess) {
            this.delegate = delegate;
            this.cacheAccess = cacheAccess;
        }

        @Override
        public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
            try {
                return delegate.getAvailableVersions(spec);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not list available versions for '%s'.", spec.getDisplayName()), e);
            }
        }

        @Override
        public File populate(final File versionDir, final VersionRef ref, final VersionControlSpec spec) {
            return cacheAccess.useCache(new Factory<File>() {
                @Nullable
                @Override
                public File create() {
                    GFileUtils.mkdirs(versionDir);
                    GFileUtils.touch(versionDir);
                    try {
                        return delegate.populate(versionDir, ref, spec);
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not populate %s from '%s'.", versionDir, spec.getDisplayName()), e);
                    }
                }
            });
        }
    }
}
