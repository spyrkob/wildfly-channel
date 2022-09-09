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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
public class Channel implements AutoCloseable {

    public static final String CLASSIFIER="channel";
    public static final String EXTENSION="yaml";

    /**
     * Strategies for resolving artifact versions if it is not listed in streams.
     * <ul>
     *    <li>LATEST - Use the latest version according to {@link VersionMatcher#COMPARATOR}</li>
     *    <li>ORIGINAL - Use the {@code baseVersion} if provided in the query</li>
     *    <li>MAVEN_LATEST - Use the value of {@code <latest>} in maven-metadata.xml</li>
     *    <li>MAVEN_RELEASE - Use the value of {@code <release>} in maven-metadata.xml</li>
     *    <li>NONE - throw {@link UnresolvedMavenArtifactException}</li>
     * </ul>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum NoStreamStrategy {
        @JsonProperty("latest")
        LATEST,
        @JsonProperty("original")
        ORIGINAL,
        @JsonProperty("maven-latest")
        MAVEN_LATEST,
        @JsonProperty("maven-release")
        MAVEN_RELEASE,
        @JsonProperty("none")
        NONE
    }

    /** Version of the schema used by this channel.
     * This is a required field.
     */
    private final String schemaVersion;

    /**
     * Name of the channel (as an one-line human readable description of the channel).
     * This is an optional field.
     */
    private final String name;

    /**
     * Description of the channel. It can use multiple lines.
     * This is an optional field.
     */
    private final String description;

    /**
     * Vendor of the channel.
     * This is an optional field.
     */
    private final Vendor vendor;
    private final List<Repository> repositories;
    private BlocklistCoordinate blocklistCoordinate;

    /**
     * Required channels
     */
    List<Channel> requiredChannels = Collections.emptyList();

    /**
     * Strategy for resolving artifact versions if it is not listed in streams.
     * This is an optional field. If not specified 'LATEST' strategy is used.
     */
    private NoStreamStrategy noStreamStrategy;

    /**
     * Optional channel manifest specifying streams.
     */
    private ChannelManifest channelManifest;

    /**
     * Optional coordinates (either URL or GA[V]) of a channel manifest
     */
    private ChannelManifestCoordinate manifestCoordinate;

    private MavenVersionsResolver resolver;

    @JsonIgnore
    public ChannelManifest getManifest() {
        return channelManifest;
    }

    /**
     * Representation of a Channel resource using the current schema version.
     *
     * @see #Channel(String, String, String, Vendor, List, ChannelManifestCoordinate, BlocklistCoordinate, NoStreamStrategy)
     */
    public Channel(String name,
                   String description,
                   Vendor vendor,
                   List<Repository> repositories,
                   ChannelManifestCoordinate manifestCoordinate,
                   BlocklistCoordinate blocklistCoordinate,
                   NoStreamStrategy noStreamStrategy){
        this(ChannelMapper.CURRENT_SCHEMA_VERSION,
                name,
                description,
                vendor,
                repositories,
                manifestCoordinate,
                blocklistCoordinate,
                noStreamStrategy);
    }

    /**
     * Representation of a Channel resource
     *
     * @param schemaVersion the version of the schema to validate this channel resource - required
     * @param name the name of the channel - can be {@code null}
     * @param description the description of the channel - can be {@code null}
     * @param vendor the vendor of the channel - can be {@code null}
     * @param noStreamStrategy - strategy for resolving version if the artifact is not listed in steams - can be {@code null}
     */
    @JsonCreator
    @JsonPropertyOrder({ "schemaVersion", "name", "description", "vendor", "requires", "streams" })
    public Channel(@JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
                   @JsonProperty(value = "name") String name,
                   @JsonProperty(value = "description") String description,
                   @JsonProperty(value = "vendor") Vendor vendor,
                   @JsonProperty(value = "repositories")
                   @JsonInclude(NON_EMPTY) List<Repository> repositories,
                   @JsonProperty(value = "manifest") ChannelManifestCoordinate manifestCoordinate,
                   @JsonProperty(value = "blocklist") @JsonInclude(NON_EMPTY) BlocklistCoordinate blocklistCoordinate,
                   @JsonProperty(value = "resolves-if-no-stream") NoStreamStrategy noStreamStrategy) {
        this.schemaVersion = schemaVersion;
        this.name = name;
        this.description = description;
        this.vendor = vendor;
        this.repositories = (repositories != null) ? repositories : emptyList();
        this.manifestCoordinate = manifestCoordinate;
        this.blocklistCoordinate = blocklistCoordinate;
        this.noStreamStrategy = (noStreamStrategy != null) ? noStreamStrategy: NoStreamStrategy.ORIGINAL;
    }

    @JsonInclude
    public String getSchemaVersion() {
        return schemaVersion;
    }

    @JsonInclude(NON_NULL)
    public String getName() {
        return name;
    }

    @JsonInclude(NON_NULL)
    public String getDescription() {
        return description;
    }

    @JsonInclude(NON_NULL)
    public Vendor getVendor() {
        return vendor;
    }

    @JsonProperty(value = "repositories")
    public List<Repository> getRepositories() {
        return repositories;
    }

    @JsonProperty(value = "manifest")
    public ChannelManifestCoordinate getManifestRef() {
        return manifestCoordinate;
    }
    @JsonInclude(NON_EMPTY)
    public NoStreamStrategy getNoStreamStrategy() {
        return noStreamStrategy;
    }

    // marks an instance of Channel as dependency of another channel
    @JsonIgnore
    private boolean dependency = false;

    @JsonIgnore
    public Optional<Blocklist> blocklist = Optional.empty();

    /**
     *
     * @param factory
     * @param channels
     * @throws UnresolvedRequiredManifestException - if a required manifest cannot be resolved either via maven coordinates or in the list of channels
     * @throws CyclicDependencyException - if the required manifests form a cyclic dependency
     */
    void init(MavenVersionsResolver.Factory factory, List<Channel> channels) {
        if (resolver != null) {
            //already initialized
            return;
        }

        resolver = factory.create(repositories);

        if (manifestCoordinate != null) {
            channelManifest = resolveManifest(manifestCoordinate);
        } else {
            channelManifest = new ChannelManifest(null, null, null, Collections.emptyList());
        }

        final List<ManifestRequirement> manifestRequirements = channelManifest.getManifestRequirements();
        if (!manifestRequirements.isEmpty()) {
            requiredChannels = new ArrayList<>();
        }
        for (ManifestRequirement manifestRequirement : manifestRequirements) {
            Channel foundChannel = findRequiredChannel(factory, channels, manifestRequirement);
            requiredChannels.add(foundChannel);
        }

        if (blocklistCoordinate != null) {
            String groupId = blocklistCoordinate.getGroupId();
            String artifactId = blocklistCoordinate.getArtifactId();
            String version = blocklistCoordinate.getVersion();
            if (version == null) {
                final Set<String> versions = resolver.getAllVersions(groupId, artifactId,
                        BlocklistCoordinate.EXTENSION, BlocklistCoordinate.CLASSIFIER);
                Optional<String> latest = VersionMatcher.getLatestVersion(versions);
                if (latest.isPresent()) {
                    version = latest.get();
                }
            }

            if (version != null) {
                final File file = resolver.resolveArtifact(groupId,
                        artifactId,
                        BlocklistCoordinate.EXTENSION,
                        BlocklistCoordinate.CLASSIFIER,
                        version);
                try {
                    blocklist = Optional.of(Blocklist.from(file.toURI().toURL()));
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                blocklist = Optional.empty();
            }
        }
    }

    private Channel findRequiredChannel(MavenVersionsResolver.Factory factory, List<Channel> channels, ManifestRequirement manifestRequirement) {
        Channel foundChannel = null;
        for (Channel c: channels) {
            if (c.getManifest() == null) {
                c.init(factory, channels);
            }
            if (manifestRequirement.getId().equals(c.getManifest().getId())) {
                foundChannel = c;
                break;
            }
        }

        if (foundChannel == null) {
            if (manifestRequirement.getMavenCoordinate() == null) {
                throw new UnresolvedRequiredManifestException("Manifest with ID " + manifestRequirement.getId() + " is not available", manifestRequirement.getId());
            }
            foundChannel = createNewChannelFromMaven(factory, channels, manifestRequirement);
        }

        checkForCycles(foundChannel);

        foundChannel.markAsDependency();
        return foundChannel;
    }

    private Channel createNewChannelFromMaven(MavenVersionsResolver.Factory factory, List<Channel> channels, ManifestRequirement manifestRequirement) {
        String groupId = manifestRequirement.getGroupId();
        String artifactId = manifestRequirement.getArtifactId();
        String version = manifestRequirement.getVersion();
        if (version == null) {
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER);
            Optional<String> latest = VersionMatcher.getLatestVersion(versions);
            version = latest.orElseThrow(() -> new RuntimeException(String.format("Can not determine the latest version for Maven artifact %s:%s:%s:%s",
                    groupId, artifactId, ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER)));
        }
        final Channel requiredChannel = new Channel(null, null, null, repositories,
                new ChannelManifestCoordinate(groupId, artifactId, version), null,
                NoStreamStrategy.NONE);
        try {
            requiredChannel.init(factory, channels);
        } catch (UnresolvedMavenArtifactException e) {
            throw new UnresolvedRequiredManifestException("Manifest with ID " + manifestRequirement.getId() + " is not available", manifestRequirement.getId(), e);
        }
        return requiredChannel;
    }

    private void checkForCycles(Channel foundChannel) {
        final String manifestId = this.getManifest().getId();
        if (foundChannel.getManifest().getId() != null && foundChannel.getManifest().getId().equals(manifestId)) {
            throw new CyclicDependencyException("Channel depends on itself: " + manifestId);
        }
        if (foundChannel.requiredChannels.stream().map(Channel::getManifest).map(ChannelManifest::getId).filter((id)->id != null && id.equals(manifestId)).findFirst().isPresent()) {
            throw new CyclicDependencyException("Channels form a cyclic dependency: " + manifestId + " -> " + foundChannel.getManifest().getId());
        }
        for (Channel requiredChannel : foundChannel.requiredChannels) {
            checkForCycles(requiredChannel);
        }
    }

    @Override
    public void close() {
        if (resolver != null) {
            for (Channel requiredChannel : requiredChannels) {
                requiredChannel.close();
            }
            this.resolver.close();
            this.resolver = null;
        }
    }
    private void markAsDependency() {
        this.dependency = true;
    }

    boolean isDependency() {
        return dependency;
    }

    public BlocklistCoordinate getBlocklistCoordinate() {
        return blocklistCoordinate;
    }

    static class ResolveLatestVersionResult {
        final String version;
        final Channel channel;

        ResolveLatestVersionResult(String version, Channel channel) {
            this.version = version;
            this.channel = channel;
        }
    }

    private ChannelManifest resolveManifest(ChannelManifestCoordinate manifestCoordinate) throws UnresolvedMavenArtifactException {
        return resolver.resolveChannelMetadata(List.of(manifestCoordinate))
                .stream()
                .map(ChannelManifestMapper::from)
                .findFirst().orElseThrow();
    }

    Optional<ResolveLatestVersionResult> resolveLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(resolver);

        // first we find if there is a stream for that given (groupId, artifactId).
        Optional<Stream> foundStream = channelManifest.findStreamFor(groupId, artifactId);
        // no stream for this artifact, let's look into the required channel
        if (!foundStream.isPresent()) {
            // we return the latest value from the required channels
            Map<String, Channel> foundVersions = new HashMap<>();
            for (Channel requiredChannel : requiredChannels) {
                Optional<Channel.ResolveLatestVersionResult> found = requiredChannel.resolveLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
                if (found.isPresent()) {
                    foundVersions.put(found.get().version, found.get().channel);
                }
            }
            Optional<String> foundVersionInRequiredChannels = foundVersions.keySet().stream().sorted(COMPARATOR.reversed()).findFirst();
            if (foundVersionInRequiredChannels.isPresent()) {
                return Optional.of(new ResolveLatestVersionResult(foundVersionInRequiredChannels.get(), foundVersions.get(foundVersionInRequiredChannels.get())));
            }

            // finally try the NoStreamStrategy
            switch (noStreamStrategy) {
                case ORIGINAL:
                    return Optional.of(new ResolveLatestVersionResult(baseVersion, this));
                case LATEST:
                    Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
                    final Optional<String> latestVersion = versions.stream().sorted(COMPARATOR.reversed()).findFirst();
                    if (latestVersion.isPresent()) {
                        return Optional.of(new ResolveLatestVersionResult(latestVersion.get(), this));
                    } else {
                        return Optional.empty();
                    }
                case MAVEN_LATEST:
                    String latestMetadataVersion = resolver.getMetadataLatestVersion(groupId, artifactId);
                    return Optional.of(new ResolveLatestVersionResult(latestMetadataVersion, this));
                case MAVEN_RELEASE:
                    String releaseMetadataVersion = resolver.getMetadataReleaseVersion(groupId, artifactId);
                    return Optional.of(new ResolveLatestVersionResult(releaseMetadataVersion, this));
                default:
                    return Optional.empty();
            }
        }

        Stream stream = foundStream.get();
        Optional<String> foundVersion = Optional.empty();
        // there is a stream, let's now check its version
        if (stream.getVersion() != null) {
            foundVersion = Optional.of(stream.getVersion());
        } else if (stream.getVersionPattern() != null) {
            // if there is a version pattern, we resolve all versions from Maven to find the latest one
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
            if (this.blocklist.isPresent()) {
                final Set<String> blocklistedVersions = this.blocklist.get().getVersionsFor(groupId, artifactId);

                versions.removeAll(blocklistedVersions);
            }
            foundVersion = foundStream.get().getVersionComparator().matches(versions);
        }

        if (foundVersion.isPresent()) {
            return Optional.of(new ResolveLatestVersionResult(foundVersion.get(), this));
        }
        return Optional.empty();
    }

    MavenArtifact resolveDirectMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        File file = resolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        return new MavenArtifact(groupId, artifactId, extension, classifier, version, file);
    }

    List<MavenArtifact> resolveDirectMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        coordinates.stream().forEach(c->{
            requireNonNull(c.getGroupId());
            requireNonNull(c.getArtifactId());
            requireNonNull(c.getVersion());
        });
        final List<File> files = resolver.resolveArtifacts(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            final ArtifactCoordinate request = coordinates.get(i);
            final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), files.get(i));

            res.add(resolvedArtifact);
        }
        return res;
    }

    static class ResolveArtifactResult {
        File file;
        Channel channel;

        ResolveArtifactResult(File file, Channel channel) {
            this.file = file;
            this.channel = channel;
        }
    }

    ResolveArtifactResult resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
        requireNonNull(resolver);

        // first we looked into the required channels
        for (Channel requiredChannel : requiredChannels) {
            try {
                return requiredChannel.resolveArtifact(groupId, artifactId, extension, classifier, version);
            } catch (UnresolvedMavenArtifactException e) {
                // ignore if the required channel are not able to resolve the artifact
            }
        }

        return new ResolveArtifactResult(resolver.resolveArtifact(groupId, artifactId, extension, classifier, version), this);
    }

    List<ResolveArtifactResult> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        final List<File> resolvedArtifacts = resolver.resolveArtifacts(coordinates);
        return resolvedArtifacts.stream().map(f->new ResolveArtifactResult(f, this)).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Channel{" +
                "schemaVersion='" + schemaVersion + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", vendor=" + vendor +
                ", manifestRef=" + manifestCoordinate +
                '}';
    }
}
