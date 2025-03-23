/*
 * Copyright (c) 2023 brokiem
 * This project is licensed under the MIT License
 */

 package com.brokiem.bedrockreplay.utils;

 import com.alibaba.fastjson.JSONObject;
 import org.jose4j.jws.AlgorithmIdentifiers;
 import org.jose4j.jws.JsonWebSignature;
 import org.jose4j.jwt.JwtClaims;
 import org.jose4j.lang.JoseException;
 
 import java.net.URI;
 import java.security.KeyPair;
 import java.util.Base64;
 
 public class JwtUtils {
 
     public static String encodeJWT(KeyPair keyPair, JSONObject payload) {
         try {
             // Create a new JWT Claims object
             JwtClaims claims = new JwtClaims();
             payload.forEach(claims::setClaim); // Manually set claims to avoid parsing errors
 
             // Convert public key to Base64 and create x5u URI
             String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
             URI x5u = URI.create(publicKeyBase64);
 
             // Create the JWT
             JsonWebSignature jws = new JsonWebSignature();
             jws.setPayload(claims.toJson());
             jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
             jws.setHeader("x5u", x5u.toString());
             jws.setKey(keyPair.getPrivate());
 
             // Sign the JWT
             return jws.getCompactSerialization();
         } catch (JoseException e) {
             throw new RuntimeException("Failed to create JWT", e);
         }
     }
 }
 