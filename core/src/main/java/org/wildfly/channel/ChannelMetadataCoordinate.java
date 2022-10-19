package org.wildfly.channel;

import java.net.URL;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A coordinate of Channel metadata artefacts (channel or manifest).
 * Uses either a Maven coordinates (groupId, artifactId, version)
 * or a URL from which the metadata file can be fetched.
 */
public class ChannelMetadataCoordinate {
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String extension;

    private URL url;

    protected ChannelMetadataCoordinate() {
    }

    public ChannelMetadataCoordinate(String groupId, String artifactId, String version, String classifier, String extension) {
        this(groupId, artifactId, version, classifier, extension, null);
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
    }

    public ChannelMetadataCoordinate(String groupId, String artifactId, String classifier, String extension) {
        this(groupId, artifactId, null, classifier, extension, null);
        requireNonNull(groupId);
        requireNonNull(artifactId);
    }

    public ChannelMetadataCoordinate(URL url) {
        this(null, null, null, null, null, url);
        requireNonNull(url);
    }

    private ChannelMetadataCoordinate(String groupId, String artifactId, String version, String classifier, String extension, URL url) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
        this.url = url;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public URL getUrl() {
        return url;
    }

    public String getClassifier() {
        return classifier;
    };

    public String getExtension() {
        return extension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelMetadataCoordinate that = (ChannelMetadataCoordinate) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version) && Objects.equals(classifier, that.classifier) && Objects.equals(extension, that.extension) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, extension, url);
    }
}
