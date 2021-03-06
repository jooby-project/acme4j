/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2016 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.shredzone.acme4j.util.TestUtils.getJson;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.junit.Test;
import org.shredzone.acme4j.connector.Resource;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.shredzone.acme4j.provider.TestableConnectionProvider;
import org.shredzone.acme4j.util.ClaimBuilder;
import org.shredzone.acme4j.util.TestUtils;

/**
 * Unit tests for {@link Certificate}.
 */
public class CertificateTest {

    private URI resourceUri = URI.create("http://example.com/acme/resource");
    private URI locationUri = URI.create("http://example.com/acme/certificate");
    private URI chainUri    = URI.create("http://example.com/acme/chain");

    /**
     * Test that a certificate can be downloaded.
     */
    @Test
    public void testDownload() throws AcmeException, IOException {
        final X509Certificate originalCert = TestUtils.createCertificate();

        TestableConnectionProvider provider = new TestableConnectionProvider() {
            private boolean isLocationUri;

            @Override
            public int sendRequest(URI uri) {
                assertThat(uri, isOneOf(locationUri, chainUri));
                isLocationUri = uri.equals(locationUri);
                return HttpURLConnection.HTTP_OK;
            }

            @Override
            public X509Certificate readCertificate() {
                return originalCert;
            }

            @Override
            public URI getLink(String relation) {
                switch(relation) {
                    case "up": return (isLocationUri ? chainUri : null);
                    default: return null;
                }
            }
        };

        Certificate cert = new Certificate(provider.createSession(), locationUri);
        X509Certificate downloadedCert = cert.download();
        assertThat(downloadedCert, is(sameInstance(originalCert)));
        assertThat(cert.getChainLocation(), is(chainUri));

        X509Certificate[] downloadedChain = cert.downloadChain();
        assertThat(downloadedChain.length, is(1));
        assertThat(downloadedChain[0], is(sameInstance(originalCert)));

        provider.close();
    }

    /**
     * Test that a {@link AcmeRetryAfterException} is thrown.
     */
    @Test
    public void testRetryAfter() throws AcmeException, IOException {
        final long retryAfter = System.currentTimeMillis() + 30 * 1000L;

        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public int sendRequest(URI uri) {
                assertThat(uri, is(locationUri));
                return HttpURLConnection.HTTP_ACCEPTED;
            }

            @Override
            public Date getRetryAfterHeader() {
                return new Date(retryAfter);
            }
        };

        Certificate cert = new Certificate(provider.createSession(), locationUri);

        try {
            cert.download();
            fail("Expected AcmeRetryAfterException");
        } catch (AcmeRetryAfterException ex) {
            assertThat(ex.getRetryAfter(), is(new Date(retryAfter)));
        }

        provider.close();
    }

    /**
     * Test that a certificate can be revoked.
     */
    @Test
    public void testRevokeCertificate() throws AcmeException, IOException {
        final X509Certificate originalCert = TestUtils.createCertificate();

        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public int sendSignedRequest(URI uri, ClaimBuilder claims, Session session) {
                assertThat(uri, is(resourceUri));
                assertThat(claims.toString(), sameJSONAs(getJson("revokeCertificateRequest")));
                assertThat(session, is(notNullValue()));
                return HttpURLConnection.HTTP_OK;
            }
        };

        provider.putTestResource(Resource.REVOKE_CERT, resourceUri);

        Certificate cert = new Certificate(provider.createSession(), locationUri, null, originalCert);
        cert.revoke();

        provider.close();
    }

    /**
     * Test that a certificate can be revoked with reason.
     */
    @Test
    public void testRevokeCertificateWithReason() throws AcmeException, IOException {
        final X509Certificate originalCert = TestUtils.createCertificate();

        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public int sendSignedRequest(URI uri, ClaimBuilder claims, Session session) {
                assertThat(uri, is(resourceUri));
                assertThat(claims.toString(), sameJSONAs(getJson("revokeCertificateWithReasonRequest")));
                assertThat(session, is(notNullValue()));
                return HttpURLConnection.HTTP_OK;
            }
        };

        provider.putTestResource(Resource.REVOKE_CERT, resourceUri);

        Certificate cert = new Certificate(provider.createSession(), locationUri, null, originalCert);
        cert.revoke(RevocationReason.KEY_COMPROMISE);

        provider.close();
    }

}
