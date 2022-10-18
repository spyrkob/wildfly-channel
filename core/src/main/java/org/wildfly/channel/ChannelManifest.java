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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class ChannelManifest implements AutoCloseable {

    public static final String CLASSIFIER="manifest";
    public static final String EXTENSION="yaml";

    private final String schemaVersion;

    private final String name;

    private final String description;

    private Set<Stream> streams;

    private MavenVersionsResolver resolver;

    public ChannelManifest(String name,
                           String description,
                           Collection<Stream> streams) {
        this(ChannelManifestMapper.CURRENT_SCHEMA_VERSION,
                name,
                description,
                streams);
    }

    @JsonCreator
    @JsonPropertyOrder({ "schemaVersion", "name", "description", "streams" })
    public ChannelManifest(@JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
                           @JsonProperty(value = "name") String name,
                           @JsonProperty(value = "description") String description,
                           @JsonProperty(value = "streams") Collection<Stream> streams) {
        this.schemaVersion = schemaVersion;
        this.name = name;
        this.description = description;
        this.streams = new TreeSet<>();
        if (streams != null) {
            this.streams.addAll(streams);
        }
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

    @JsonInclude(NON_EMPTY)
    public Collection<Stream> getStreams() {
        return streams;
    }

    void init(MavenVersionsResolver factory) {
        resolver = resolver;
    }

    @Override
    public void close() {
        this.resolver.close();
        this.resolver = null;
    }

    public Optional<Stream> findStreamFor(String groupId, String artifactId) {
        // first exact match:
        Optional<Stream> stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals(artifactId)).findFirst();
        if (stream.isPresent()) {
            return stream;
        }
        // check if there is a stream for groupId:*
        stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals("*")).findFirst();
        return stream;
    }
}
