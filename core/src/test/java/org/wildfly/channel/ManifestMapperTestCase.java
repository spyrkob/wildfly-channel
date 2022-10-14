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

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ManifestMapperTestCase {

    @Test
    public void testWriteReadChannel() throws Exception {
        final Manifest manifest = new Manifest("test_name", "test_desc", Collections.emptyList());
        final String yaml = ManifestMapper.toYaml(manifest);

        final Manifest manifest1 = ManifestMapper.fromString(yaml);
        assertEquals("test_desc", manifest1.getDescription());
    }

    @Test
    public void testWriteMultipleChannels() throws Exception {
        final ChannelRequirement req = new ChannelRequirement("org", "foo", "1.2.3");
        final Stream stream1 = new Stream("org.bar", "example", "1.2.3");
        final Stream stream2 = new Stream("org.bar", "other-example", Pattern.compile("\\.*"));
        final Manifest manifest1 = new Manifest("test_name_1", "test_desc", Arrays.asList(stream1, stream2));
        final Manifest manifest2 = new Manifest("test_name_2", "test_desc", Collections.emptyList());
        final String yaml1 = ManifestMapper.toYaml(manifest1);
        final String yaml2 = ManifestMapper.toYaml(manifest2);

        System.out.println(yaml1);
        System.out.println(yaml2);

        final Manifest m1 = ManifestMapper.fromString(yaml1);
        assertEquals(manifest1.getName(), m1.getName());
        assertEquals(2, m1.getStreams().size());
        assertEquals("example", m1.getStreams().stream().findFirst().get().getArtifactId());
        final Manifest m2 = ManifestMapper.fromString(yaml2);
        assertEquals(manifest2.getName(), m2.getName());
        assertEquals(0, m2.getStreams().size());
    }

    @Test
    public void testReadChannelWithUnknownProperties() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/manifest-with-unknown-properties.yaml");

        Channel channel = ChannelMapper.from(file);
        assertNotNull(channel);
    }
}
