/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.channel.maven;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;
import org.jboss.logging.Logger;

public class VersionResolverFactory implements MavenVersionsResolver.Factory {

    private static final Logger LOG = Logger.getLogger(VersionResolverFactory.class);

    public static final RepositoryPolicy DEFAULT_REPOSITORY_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    private final RepositorySystem system;
    private final RepositorySystemSession session;

    public VersionResolverFactory(RepositorySystem system,
                                  RepositorySystemSession session) {
        this.system = system;
        this.session = session;
    }

    @Override
    public MavenVersionsResolver create(Collection<Repository> repositories) {
        Objects.requireNonNull(repositories);

        final List<RemoteRepository> mvnRepositories = repositories.stream()
                .map(r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl())
                        .setPolicy(DEFAULT_REPOSITORY_POLICY)
                        .build())
                .collect(Collectors.toList());
        return create(mvnRepositories);
    }

    private MavenResolverImpl create(List<RemoteRepository> mvnRepositories) {
        return new MavenResolverImpl(system, session, mvnRepositories);
    }

    private class MavenResolverImpl implements MavenVersionsResolver {

        private final RepositorySystem system;
        private final RepositorySystemSession session;
        private final List<RemoteRepository> repositories;

        MavenResolverImpl(RepositorySystem system,
                          RepositorySystemSession session,
                          List<RemoteRepository> repositories) {
            this.system = system;
            this.session = session;
            this.repositories = repositories;
        }

        @Override
        public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
            requireNonNull(groupId);
            requireNonNull(artifactId);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
            VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
            versionRangeRequest.setArtifact(artifact);
            if (repositories != null) {
                versionRangeRequest.setRepositories(repositories);
            }

            try {
                VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
                Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
                return versions;
            } catch (VersionRangeResolutionException e) {
                return emptySet();
            }
        }

        @Override
        public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
            requireNonNull(groupId);
            requireNonNull(artifactId);
            requireNonNull(version);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            if (repositories != null) {
                request.setRepositories(repositories);
            }

            ArtifactResult result;
            try {
                result = system.resolveArtifact(session, request);
            } catch (ArtifactResolutionException ex) {
                throw new UnresolvedMavenArtifactException(ex.getLocalizedMessage(), ex, singleton(new ArtifactCoordinate(groupId, artifactId, extension, classifier, version)));
            }
            return result.getArtifact().getFile();
        }

        @Override
        public List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
            requireNonNull(coordinates);

            List<ArtifactRequest> requests = new ArrayList<>();
            for (ArtifactCoordinate coord : coordinates) {
                Artifact artifact = new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), coord.getClassifier(), coord.getExtension(), coord.getVersion());

                ArtifactRequest request = new ArtifactRequest();
                request.setArtifact(artifact);
                if (repositories != null) {
                    request.setRepositories(repositories);
                }
                requests.add(request);
            }

            try {
                final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);
                // results are in the same order as requests
                return artifactResults.stream()
                   .map(ArtifactResult::getArtifact)
                   .map(Artifact::getFile)
                   .collect(Collectors.toList());
            } catch (ArtifactResolutionException ex) {
                Set<ArtifactCoordinate> failed = ex.getResults().stream()
                   .filter(r->r.getArtifact() == null)
                   .map(res->res.getRequest().getArtifact())
                   .map(a->new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion()))
                   .collect(Collectors.toSet());
                throw new UnresolvedMavenArtifactException(ex.getLocalizedMessage(), ex, failed);
            }
        }
    }

    /**
     * Resolve and read the channels at the specified coordinates.
     *
     * If the {@code ChannelCoordinate} specifies a URL, the channel will be fetched from this URL (without reading the Maven coordinates).
     * If the {@code url} is {@code null}, the channel will be resolved as a Maven coordinate. If the {@code version} is null, the channel artifact will
     * be resolved as the latest version according to the Maven repositories metadata, otherwise the exact artifact for the specified {@code version} will be resolved.
     *
     * @param channelCoords A list of Channel coordinates
     * @return a List of resolved channels
     * @throws UnresolvedMavenArtifactException if the channels can not be resolved
     * @throws MalformedURLException if the channel's rul is not properly formed
     */
    public List<Channel> resolveChannels(List<ChannelCoordinate> channelCoords, List<RemoteRepository> repositories) throws UnresolvedMavenArtifactException, MalformedURLException {
        requireNonNull(channelCoords);

        List<Channel> channels = new ArrayList<>();
        try (MavenVersionsResolver resolver = create(repositories)) {

            for (ChannelCoordinate channelCoord : channelCoords) {
                if (channelCoord.getUrl() != null) {
                    Channel channel = ChannelMapper.from(channelCoord.getUrl());
                    LOG.infof("Resolving channel at %s", channelCoord.getUrl());
                    channels.add(channel);
                    continue;
                }

                String version = channelCoord.getVersion();
                if (version == null) {
                    Set<String> versions = resolver.getAllVersions(channelCoord.getGroupId(), channelCoord.getArtifactId(), channelCoord.getExtension(), channelCoord.getClassifier());
                    Optional<String> latestVersion = VersionMatcher.getLatestVersion(versions);
                    version = latestVersion.orElseThrow(() -> {
                        throw new UnresolvedMavenArtifactException(String.format("Unable to resolve the latest version of channel %s:%s", channelCoord.getGroupId(), channelCoord.getArtifactId()));
                    });
                }
                LOG.infof("Resolving channel from Maven artifact %s:%s:%s", channelCoord.getGroupId(), channelCoord.getArtifactId(), version);
                File channelArtifact = resolver.resolveArtifact(channelCoord.getGroupId(), channelCoord.getArtifactId(), channelCoord.getExtension(), channelCoord.getClassifier(), version);
                Channel channel = ChannelMapper.from(channelArtifact.toURI().toURL());
                channels.add(channel);
            }
        }
        return channels;
    }
}


