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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

public class ChannelWithBlocklistTestCase {

    @TempDir
    private Path tempDir;

    @Test
    public void testFindLatestMavenArtifactVersion() throws Exception {
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionBlocklistDoesntExist() throws Exception {
        final String manifest =
              "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
              "streams:\n" +
              "  - groupId: org.wildfly\n" +
              "    artifactId: '*'\n" +
              "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
            .thenReturn(Collections.emptyList());
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.1.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionWithWildcardBlocklist() throws Exception {
        final String manifest =
              "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
              "streams:\n" +
              "  - groupId: org.wildfly\n" +
              "    artifactId: '*'\n" +
              "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist-with-wildcards.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionBlocklistsAllVersionsException() throws Exception {
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(singleton("25.0.1.Final")));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            try {
                session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "26.0.0.Final");
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifact() throws Exception {
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.0.Final", "25.0.1.Final")));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final")).thenReturn(resolvedArtifactFile);
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertNotNull(artifact);

            assertEquals("org.wildfly", artifact.getGroupId());
            assertEquals("wildfly-ee-galleon-pack", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("25.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifactThrowUnresolvedMavenArtifactException() throws Exception {
        final String manifest =
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "schemaVersion: 1.0.0\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.1.Final","26.0.0.Final")));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            try {
                session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveMavenArtifactsFromOneChannel() throws Exception {
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: wildfly-ee-galleon-pack\n" +
                "    versionPattern: \".*\"\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: wildfly-cli\n" +
                "    version: [\"26.0.0.Final\"]";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
           .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.1.Final","25.0.0.Final")));
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final"),
           new ArtifactCoordinate("org.wildfly", "wildfly-cli", null, null, "26.0.0.Final"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
               new MavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final", resolvedArtifactFile1),
               new MavenArtifact("org.wildfly", "wildfly-cli", null, null, "26.0.0.Final", resolvedArtifactFile2)
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.wildfly", "wildfly-ee-galleon-pack");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.wildfly", "wildfly-cli");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionInRequiredChannel() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
           "schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
              "requires:\n" +
              "  - groupId: org.foo\n" +
              "    artifactId: required-channel\n" +
              "repositories:\n" +
              "  - id: test\n" +
              "    url: http://test.te");
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: wildfly-ee-galleon-pack\n" +
                "    versionPattern: \".*\"";
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/channel-with-blocklist.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        URL resolvedBlocklistURL = tccl.getResource("channels/test-blocklist.yaml");
        final Path manifestFile = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestFile, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.foo", "required-channel", "yaml", "channel"))
           .thenReturn(Set.of("1"));
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "1"))
           .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "blocklist", "yaml")))))
           .thenReturn(List.of(resolvedBlocklistURL));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));
        when(resolver.resolveChannelMetadata(List.of(new ChannelManifestCoordinate("org.test", "manifest"))))
                .thenReturn(List.of(manifestFile.toUri().toURL()));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(3)).close();
    }

    @Test
    public void testChannelWithInvalidBlacklist() throws Exception {
        final String manifest =
              "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
              "streams:\n" +
              "  - groupId: org.wildfly\n" +
              "    artifactId: '*'\n" +
              "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedBlocklistURL = tccl.getResource("channels/invalid-blocklist.yaml");
        File resolvedBlocklistFile = Paths.get(resolvedBlocklistURL.toURI()).toFile();

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
                .thenReturn(List.of(resolvedBlocklistFile.toURI().toURL()));
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            fail("InvalidChannelException should have been thrown.");
        } catch (InvalidChannelException e) {
            assertEquals(2, e.getValidationMessages().size());
            assertTrue(e.getValidationMessages().get(0).contains("version: is missing"), e.getValidationMessages().get(0));
        }
    }

    // TODO: blocklist with versionPattern
    @Test
    public void testBlocklistWithVersionPattern() throws Exception {
        final String manifest =
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveChannelMetadata(eq(List.of(
                new ChannelMetadataCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0", "blocklist", "yaml")))))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist-with-versionPattern.yaml")));
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.0.Final", "25.0.1.Final")));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final")).thenReturn(resolvedArtifactFile);
        final List<Channel> channels = mockChannelWithBlocklist(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertNotNull(artifact);

            assertEquals("org.wildfly", artifact.getGroupId());
            assertEquals("wildfly-ee-galleon-pack", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("25.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }

        verify(resolver, times(2)).close();
    }

    private static List<Channel> mockChannelWithBlocklist(MavenVersionsResolver resolver, Path tempDir, String... manifests) throws IOException {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < manifests.length; i++) {
            channels.add(new Channel(null, null, null, null,
                    emptyList(),
                    new ChannelManifestCoordinate("org.channels", "channel" + i, "1.0.0"),
                    new ChannelManifestCoordinate("org.wildfly", "wildfly-blocklist", "1.0.0")));
            String manifest = manifests[i];
            Path manifestFile = Files.writeString(tempDir.resolve("manifest" + i +".yaml"), manifest);

            when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("org.channels", "channel" + i, "1.0.0")))))
                    .thenReturn(List.of(manifestFile.toUri().toURL()));
        }
        return channels;
    }

    private static void assertContainsAll(List<MavenArtifact> expected, List<MavenArtifact> actual) {
        List<MavenArtifact> testList = new ArrayList<>(expected);
        for (MavenArtifact a : actual) {
            if (!expected.contains(a)) {
                fail("Unexpected artifact " + a);
            }
            testList.remove(a);
        }
        if (!testList.isEmpty()) {
            fail("Expected artifact not found " + expected.get(0));
        }
    }
}
