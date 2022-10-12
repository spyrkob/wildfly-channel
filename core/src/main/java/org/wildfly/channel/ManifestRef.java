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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class ManifestRef {
    private final String url;
    private final String gav;
    private final String groupId;
    private final String artifactId;
    private final String version;

    @JsonCreator
    public ManifestRef(@JsonProperty(value = "url") String url, @JsonProperty(value = "gav") String gav) {
        this.url = url;
        this.gav = gav;

        if (gav != null) {
            final String[] parsedGav = gav.split(":");
            if (parsedGav.length < 2 || parsedGav.length > 3) {
                throw new IllegalArgumentException("Illegal GAV experession: " + gav);
            }
            groupId = parsedGav[0];
            artifactId = parsedGav[1];
            version = parsedGav.length == 3 ? parsedGav[2] : null;
        } else {
            groupId = null;
            artifactId = null;
            version = null;
        }
    }

    @JsonProperty(value = "gav")
    @JsonInclude(NON_NULL)
    public String getGav() {
        return gav;
    }

    @JsonProperty(value = "url")
    @JsonInclude(NON_NULL)
    public String getUrl() {
        return url;
    }

    @JsonIgnore
    public String getGroupId() {
        return groupId;
    }

    @JsonIgnore
    public String getArtifactId() {
        return artifactId;
    }

    @JsonIgnore
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ManifestRef that = (ManifestRef) o;
        return Objects.equals(url, that.url) && Objects.equals(gav, that.gav) && Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, gav, groupId, artifactId, version);
    }
}
