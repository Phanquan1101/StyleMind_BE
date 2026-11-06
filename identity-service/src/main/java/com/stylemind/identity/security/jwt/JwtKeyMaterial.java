package com.stylemind.identity.security.jwt;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public final class JwtKeyMaterial {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final RSAKey signingJwk;
    private final RSAKey publicJwk;

    public JwtKeyMaterial(
            RSAPrivateKey privateKey,
            RSAPublicKey publicKey,
            String keyId,
            RSAKey signingJwk,
            RSAKey publicJwk) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyId = keyId;
        this.signingJwk = signingJwk;
        this.publicJwk = publicJwk;
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String keyId() { return keyId; }
    public RSAKey signingJwk() { return signingJwk; }
    public RSAKey publicJwk() { return publicJwk; }

    @Override
    public String toString() {
        return "JwtKeyMaterial[privateKey=[REDACTED], publicKey=[REDACTED], keyId=" + keyId + "]";
    }
}
