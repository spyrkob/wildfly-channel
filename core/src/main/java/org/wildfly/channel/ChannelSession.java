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
package org.wildfly.channel;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.Logger;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * A ChannelSession is used to install and resolve Maven Artifacts inside a single scope.
 */
public class ChannelSession implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ChannelSession.class);
    private static final int DEFAULT_SPLIT_ARTIFACT_PARALLELISM = 10;

    private final List<ChannelImpl> channels;
    private final ChannelRecorder recorder = new ChannelRecorder();
    private final int versionResolutionParallelism;

    /**
     * Create a ChannelSession.
     *
     * @param channelDefinitions the list of channels to resolve Maven artifact
     * @param factory Factory to create {@code MavenVersionsResolver} that are performing the actual Maven resolution.
     * @throws UnresolvedRequiredManifestException - if a required manifest cannot be resolved either via maven coordinates or in the list of channels
     * @throws CyclicDependencyException - if the required manifests form a cyclic dependency
     */
    public ChannelSession(List<Channel> channelDefinitions, MavenVersionsResolver.Factory factory) {
        this(channelDefinitions, factory, DEFAULT_SPLIT_ARTIFACT_PARALLELISM);
    }

    /**
     * Create a ChannelSession.
     *
     * @param channelDefinitions the list of channels to resolve Maven artifact
     * @param factory Factory to create {@code MavenVersionsResolver} that are performing the actual Maven resolution.
     * @param versionResolutionParallelism Number of threads to use when resolving available artifact versions.
     * @throws UnresolvedRequiredManifestException - if a required manifest cannot be resolved either via maven coordinates or in the list of channels
     * @throws CyclicDependencyException - if the required manifests form a cyclic dependency
     */
    public ChannelSession(List<Channel> channelDefinitions, MavenVersionsResolver.Factory factory, int versionResolutionParallelism) {
        requireNonNull(channelDefinitions);
        requireNonNull(factory);

        List<ChannelImpl> channelList = channelDefinitions.stream().map(ChannelImpl::new).collect(Collectors.toList());
        for (ChannelImpl channel : channelList) {
            channel.init(factory, channelList);
        }
        // filter out channels marked as dependency, so that resolution starts only at top level channels
        this.channels = channelList.stream().filter(c->!c.isDependency()).collect(Collectors.toList());
        this.versionResolutionParallelism = versionResolutionParallelism;

        validateNoDuplicatedManifests();
    }

    /**
     * Get the definitions of channels used by this session. Returned version contains resolved versions
     * of channel metadata (if applicable).
     *
     * @return List of {@code RuntimeChannel}s used to resolve artifacts by this session
     */
    public List<RuntimeChannel> getRuntimeChannels() {
        return this.channels.stream()
                .map(c->new RuntimeChannel(c.getResolvedChannelDefinition(), c.getManifest(), c.getBlocklist()))
                .collect(Collectors.toList());
    }

    /**
     * Resolve the Maven artifact according to the session's channels.
     * <p>
     * In order to find the stream corresponding to the Maven artifact, the channels are searched depth-first, starting
     * with the first channel in the list and into their respective required channels.
     * Once the first stream that matches the {@code groupId} and {@artifactId} parameters is found, the Maven artifact
     * will be resolved with the version determined by this stream.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param baseVersion - can be null. The base version is required when the stream for the component specifies multiple versions and needs the base version to
     *                    determine the appropriate version to resolve.
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws NoStreamFoundException if none of the channels in the {@code ChannelSession} provides the artifact
     * @throws ArtifactTransferException if the artifact is provided by the {@code ChannelSession}, but the resolution failed
     *
     */
    public MavenArtifact resolveMavenArtifact(String groupId, String artifactId, String extension, String classifier, String baseVersion)
            throws NoStreamFoundException, ArtifactTransferException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        // baseVersion is not used at the moment but will provide essential to support advanced use cases to determine multiple streams of the same Maven component.

        ChannelImpl.ResolveLatestVersionResult result = findChannelWithLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
        String latestVersion = result.version;
        ChannelImpl channel = result.channel;

        ChannelImpl.ResolveArtifactResult artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
        recorder.recordStream(groupId, artifactId, latestVersion);
        return new MavenArtifact(groupId, artifactId, extension, classifier, latestVersion, artifact.file, artifact.channel.getResolvedChannelDefinition().getName());
    }

    /**
     * Resolve a list of Maven artifacts according to the session's channels.
     * <p>
     * In order to find the stream corresponding to the Maven artifact, the channels are searched depth-first, starting
     * with the first channel in the list and into their respective required channels.
     * Once the first stream that matches the {@code groupId} and {@artifactId} parameters is found, the Maven artifact
     * will be resolved with the version determined by this stream.
     * <p>
     * The returned list of resolved artifacts does not maintain ordering of requested coordinates
     *
     * @param coordinates list of ArtifactCoordinates to resolve
     * @return a list of resolved MavenArtifacts with resolved versions asnd files
     * @throws NoStreamFoundException if one or more of the artifact is not provided by any of the channels in the {@code ChannelSession}
     * @throws ArtifactTransferException if one or more of the artifacts is provided by the {@code ChannelSession}, but the resolution failed
     */
    public List<MavenArtifact> resolveMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        requireNonNull(coordinates);

        Map<ChannelImpl, List<ArtifactCoordinate>> channelMap = splitArtifactsPerChannel(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (ChannelImpl channel : channelMap.keySet()) {
            final List<ArtifactCoordinate> requests = channelMap.get(channel);
            final List<ChannelImpl.ResolveArtifactResult> resolveArtifactResults = channel.resolveArtifacts(requests);
            for (int i = 0; i < requests.size(); i++) {
                final ArtifactCoordinate request = requests.get(i);
                final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(),
                        request.getExtension(), request.getClassifier(), request.getVersion(),
                        resolveArtifactResults.get(i).file,
                        resolveArtifactResults.get(i).channel.getResolvedChannelDefinition().getName());

                recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion());
                res.add(resolvedArtifact);
            }
        }
        return res;
    }

    /**
     * Resolve the Maven artifact with a specific version without checking the channels.
     * <p>
     * If the artifact is resolved, a stream for it is added to the {@code getRecordedChannel}.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param version - required
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws ArtifactTransferException if the artifact can not be resolved
     */
    public MavenArtifact resolveDirectMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws ArtifactTransferException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        /*
         * when resolving a direct artifact we don't care if a channel manifest lists that artifact, we resolve it
         * if it is present in underlying repositories.
         * BUT if the channel requires a GPG check, the artifact has to still be verified.
         * Therefore, we're trying to resolve artifact from each available channel and returning the first match.
         */

        File file = null;
        UnresolvedMavenArtifactException ex = null;
        for (ChannelImpl channel : channels) {
            try {
                file = channel.resolveArtifact(groupId, artifactId, extension, classifier, version).file;
                break;
            } catch (UnresolvedMavenArtifactException e) {
                ex = e;
            }
        }

        if (file != null) {
            recorder.recordStream(groupId, artifactId, version);
            return new MavenArtifact(groupId, artifactId, extension, classifier, version, file);
        } else if (ex != null) {
            throw ex;
        } else {
            throw new ArtifactTransferException("Unable to resolve direct artifact.",
                    Set.of(new ArtifactCoordinate(groupId, artifactId, extension, classifier, version)),
                    channels.stream()
                            .map(ChannelImpl::getResolvedChannelDefinition)
                            .flatMap(cd->cd.getRepositories().stream())
                            .collect(Collectors.toSet()));
        }
    }

    /**
     * Resolve a list of Maven artifacts with a specific version without checking the channels.
     * <p>
     * If the artifact is resolved, a stream for it is added to the {@code getRecordedChannel}.
     *
     * @param coordinates - list of ArtifactCoordinates to check
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws ArtifactTransferException if the artifact can not be resolved
     */
    public List<MavenArtifact> resolveDirectMavenArtifacts(List<ArtifactCoordinate> coordinates) throws ArtifactTransferException {
        coordinates.forEach(c -> {
            requireNonNull(c.getGroupId());
            requireNonNull(c.getArtifactId());
            requireNonNull(c.getVersion());
        });

        /*
         * When resolving a "direct" artifact, we don't care if the artifact is listed in the channel's manifest,
         * only if the underlying repositories contain that artifact.
         * BUT, we still need to verify the artifact signature if the channel requires it. To achieve that, we're
         * going to query each channel in turn, taking the artifacts it was able to resolve and keeping the rest
         * to be resolved by remaining channels. At the end we should be left with no un-resolved artifacts, or have
         * a list of artifacts not available in any channels.
         * NOTE: if the same artifact is available in both GPG-enabled and GPG-disabled channel there is no guarantee
         * which channel will be queried first.
         */

        // list of artifacts that are being resolved in this step
        List<ArtifactCoordinate> currentQuery = new ArrayList<>(coordinates);
        // we need to preserve the ordering of artifacts, but that can be affected by the order of query/resolution between channels
        final HashMap<ArtifactCoordinate, Pair<File, String>> resolvedArtifacts = new HashMap<>();
        for (ChannelImpl channel : channels) {
            if (currentQuery.isEmpty()) {
                break;
            }

            try {
                final List<ChannelImpl.ResolveArtifactResult> resolved = channel.resolveArtifacts(currentQuery);
                // keep a map of AC -> File
                for (int i = 0; i < currentQuery.size(); i++) {
                    resolvedArtifacts.put(currentQuery.get(i), Pair.of(resolved.get(i).file, channel.getResolvedChannelDefinition().getName()));
                }
                // all the artifacts were resolved by this point, lets remove all artifacts from the current query
                currentQuery = Collections.emptyList();
            } catch (UnresolvedMavenArtifactException e) {
                // at the end need to map them into a correct order
                final Set<ArtifactCoordinate> unresolvedArtifacts = e.getUnresolvedArtifacts();
                // coordinates - unresolved = it should be possible to resolve those artifacts from this channel
                // we need to call resolve again, because the first call threw an exception
                currentQuery.removeAll(unresolvedArtifacts);
                final List<ChannelImpl.ResolveArtifactResult> resolved = channel.resolveArtifacts(currentQuery);
                for (int i = 0; i < currentQuery.size(); i++) {
                    resolvedArtifacts.put(currentQuery.get(i), Pair.of(resolved.get(i).file, channel.getResolvedChannelDefinition().getName()));
                }
                // unresolved - try with another channel, rinse and repeat until run out of channels or resolve all artifacts
                currentQuery = new ArrayList<>(unresolvedArtifacts);
            }
        }
        if (!currentQuery.isEmpty()) {
            throw new ArtifactTransferException("Unable to resolve some direct artifacts", new HashSet<>(currentQuery),
                    channels.stream()
                            .map(ChannelImpl::getResolvedChannelDefinition)
                            .flatMap(cd->cd.getRepositories().stream())
                            .collect(Collectors.toSet()));
        }

        // finally, build a list of resolved files in a correct order to return and record the streams
        final List<MavenArtifact> res = coordinates.stream().map(ac -> new MavenArtifact(ac.getGroupId(), ac.getArtifactId(), ac.getExtension(),
                ac.getClassifier(), ac.getVersion(), resolvedArtifacts.get(ac).getLeft(), resolvedArtifacts.get(ac).getRight())).collect(Collectors.toList());

        res.forEach(resolvedArtifact->
            recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion())
        );
        return res;
    }

    /**
     * Find the latest version of the Maven artifact in the session's channel. The artifact file will not be resolved.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param baseVersion - can be null. The base version is required when the stream for the component specifies multiple versions and needs the base version to
     *                    determine the appropriate version to resolve.
     * @return the latest version if a Maven artifact
     * @throws NoStreamFoundException if the latest version cannot be established
     */
    public VersionResult findLatestMavenArtifactVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws NoStreamFoundException {
        final ChannelImpl.ResolveLatestVersionResult channelWithLatestVersion = findChannelWithLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
        return new VersionResult(channelWithLatestVersion.version, channelWithLatestVersion.channel.getResolvedChannelDefinition().getName());
    }

    @Override
    public void close()  {
        for (ChannelImpl channel : channels) {
            channel.close();
        }
    }

    /**
     * Returns a synthetic Channel where each resolved artifacts (either with exact or latest version)
     * is defined in a {@code Stream} with a {@code version} field.
     * <p>
     * This channel can be used to reproduce the same resolution in another ChannelSession.
     *
     * @return a synthetic Channel.
     */
    public ChannelManifest getRecordedChannel() {
        return recorder.getRecordedChannel();
    }

    private void validateNoDuplicatedManifests() {
        final List<String> manifestIds = this.channels.stream().map(c -> c.getManifest().getId()).filter(Objects::nonNull).collect(Collectors.toList());
        if (manifestIds.size() != new HashSet<>(manifestIds).size()) {
            throw new RuntimeException("The same manifest is provided by one or more channels");
        }
    }

    private ChannelImpl.ResolveLatestVersionResult findChannelWithLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws NoStreamFoundException {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        Map<String, ChannelImpl.ResolveLatestVersionResult> foundVersions = new HashMap<>();
        for (ChannelImpl channel : channels) {
            Optional<ChannelImpl.ResolveLatestVersionResult> result = channel.resolveLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
            if (result.isPresent()) {
                foundVersions.put(result.get().version, result.get());
            }
        }

        // find the latest version from all the channels that defined the stream.
        Optional<String> foundLatestVersionInChannels = foundVersions.keySet().stream().max(VersionMatcher.COMPARATOR);
        return foundVersions.get(foundLatestVersionInChannels.orElseThrow(() -> {
            final ArtifactCoordinate coord = new ArtifactCoordinate(groupId, artifactId, extension, classifier, "");
            final Set<Repository> repositories = channels.stream()
                    .map(ChannelImpl::getResolvedChannelDefinition)
                    .flatMap(d -> d.getRepositories().stream())
                    .collect(Collectors.toSet());
            throw new NoStreamFoundException(
                    String.format("Can not resolve latest Maven artifact (no stream found) : %s:%s:%s:%s", groupId, artifactId, extension, classifier),
                    Collections.singleton(coord), repositories);
        }));
    }

    private Map<ChannelImpl, List<ArtifactCoordinate>> splitArtifactsPerChannel(List<ArtifactCoordinate> coordinates) {
        long start = System.currentTimeMillis();
        Map<ChannelImpl, List<ArtifactCoordinate>> channelMap = new HashMap<>();

        ForkJoinPool customThreadPool = new ForkJoinPool(versionResolutionParallelism);
        ForkJoinTask<?> task = customThreadPool.submit(() -> coordinates.parallelStream().forEach(coord -> {
            ChannelImpl.ResolveLatestVersionResult result = findChannelWithLatestVersion(coord.getGroupId(), coord.getArtifactId(),
                    coord.getExtension(), coord.getClassifier(), coord.getVersion());
            ArtifactCoordinate query = new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), result.version);
            ChannelImpl channel = result.channel;
            synchronized (ChannelSession.this) {
                if (!channelMap.containsKey(channel)) {
                    channelMap.put(channel, new ArrayList<>());
                }
                channelMap.get(channel).add(query);
            }
        }));

        try {
            task.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnresolvedMavenArtifactException) {
                // rethrow the UnresolvedMavenArtifactException if it's the cause
                throw (UnresolvedMavenArtifactException) e.getCause();
            }
            throw new RuntimeException("Unable to resolve latest artifact versions.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to resolve latest artifact versions: interrupted", e);
        } finally {
            customThreadPool.shutdown();
        }

        float total = (System.currentTimeMillis() - start) / 1000f;
        LOG.debugf("Splitting artifacts per channels took %.2f seconds", total);

        return channelMap;
    }
}
