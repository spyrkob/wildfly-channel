/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.channel.gpg;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.wildfly.channel.spi.ArtifactIdentifier;

/**
 * Validation callbacks used for example for additional logging.
 */
public interface GpgSignatureValidatorListener {

    /**
     * Called when and artifact signature was successfully verified.
     *
     * @param artifact - the ID of the artifact being verified
     * @param publicKey - the public key used to verify the artifact
     */
    void artifactSignatureCorrect(ArtifactIdentifier artifact, PGPPublicKey publicKey);

    /**
     * Called when and artifact signature was found to be invalid.
     *
     * @param artifact - the ID of the artifact being verified
     * @param publicKey - the public key used to verify the artifact
     */
    void artifactSignatureInvalid(ArtifactIdentifier artifact, PGPPublicKey publicKey);
}
