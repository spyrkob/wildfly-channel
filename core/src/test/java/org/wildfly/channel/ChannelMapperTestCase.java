/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.channel;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChannelMapperTestCase {

    @Test
    public void test() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/multiple-channels.yaml");
        List<Channel> channels = ChannelMapper.channelsFromString("---\n" +
                "id: channel-1\n" +
                "\n" +
                "---\n" +
                "id: channel-2\n");

        System.out.println("channels = " + channels);

    }

    @Test
    public void testWriteReadChannel() throws Exception {
        final Channel channel = new Channel("test_id", "test_name", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY), true, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        final String yaml = ChannelMapper.toYaml(channel);

        final Channel channel1 = ChannelMapper.fromString(yaml);
        assertEquals(Vendor.Support.COMMUNITY, channel1.getVendor().getSupport());
    }
}
