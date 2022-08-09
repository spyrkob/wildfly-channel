/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.channel;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BlocklistCoordinate {

   private String groupId;

   private String artifactId;

   private Pattern versionPattern;

   @JsonCreator
   public BlocklistCoordinate(@JsonProperty(value = "groupId") String groupId,
                              @JsonProperty(value = "artifactId") String artifactId,
                              @JsonProperty(value = "versionPattern") Pattern versionPattern) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.versionPattern = versionPattern;
   }

   public String getGroupId() {
      return groupId;
   }

   public String getArtifactId() {
      return artifactId;
   }

   public Pattern getVersionPattern() {
      return versionPattern;
   }
}
