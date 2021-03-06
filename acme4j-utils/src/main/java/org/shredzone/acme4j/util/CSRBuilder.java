/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2015 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

/**
 * Generator for a CSR (Certificate Signing Request) suitable for ACME servers.
 * <p>
 * Requires {@code Bouncy Castle}.
 */
public class CSRBuilder {
    private static final String SIGNATURE_ALG = "SHA256withRSA";
    private static final String EC_SIGNATURE_ALG = "SHA256withECDSA";

    private final X500NameBuilder namebuilder = new X500NameBuilder(X500Name.getDefaultStyle());
    private final List<String> namelist = new ArrayList<>();
    private PKCS10CertificationRequest csr = null;

    /**
     * Adds a domain name to the CSR. The first domain name added will also be the
     * <em>Common Name</em>. All domain names will be added as <em>Subject
     * Alternative Name</em>.
     * <p>
     * Note that ACME servers may not accept wildcard domains!
     */
    public void addDomain(String domain) {
        if (namelist.isEmpty()) {
            namebuilder.addRDN(BCStyle.CN, domain);
        }
        namelist.add(domain);
    }

    /**
     * Adds a {@link Collection} of domains.
     */
    public void addDomains(Collection<String> domains) {
        for (String domain : domains) {
            addDomain(domain);
        }
    }

    /**
     * Adds multiple domain names.
     */
    public void addDomains(String... domains) {
        for (String domain : domains) {
            addDomain(domain);
        }
    }

    /**
     * Sets the organization.
     * <p>
     * Note that it is at the discretion of the ACME server to accept this parameter.
     */
    public void setOrganization(String o) {
        namebuilder.addRDN(BCStyle.O, o);
    }

    /**
     * Sets the organizational unit.
     * <p>
     * Note that it is at the discretion of the ACME server to accept this parameter.
     */
    public void setOrganizationalUnit(String ou) {
        namebuilder.addRDN(BCStyle.OU, ou);
    }

    /**
     * Sets the city or locality.
     * <p>
     * Note that it is at the discretion of the ACME server to accept this parameter.
     */
    public void setLocality(String l) {
        namebuilder.addRDN(BCStyle.L, l);
    }

    /**
     * Sets the state or province.
     * <p>
     * Note that it is at the discretion of the ACME server to accept this parameter.
     */
    public void setState(String st) {
        namebuilder.addRDN(BCStyle.ST, st);
    }

    /**
     * Sets the country.
     * <p>
     * Note that it is at the discretion of the ACME server to accept this parameter.
     */
    public void setCountry(String c) {
        namebuilder.addRDN(BCStyle.C, c);
    }

    /**
     * Signs the completed CSR.
     *
     * @param keypair
     *            {@link KeyPair} to sign the CSR with
     */
    public void sign(KeyPair keypair) throws IOException {
        if (namelist.isEmpty()) {
            throw new IllegalStateException("No domain was set");
        }
        if (keypair == null) {
            throw new IllegalArgumentException("keypair must not be null");
        }

        try {
            GeneralName[] gns = new GeneralName[namelist.size()];
            for (int ix = 0; ix < namelist.size(); ix++) {
                gns[ix] = new GeneralName(GeneralName.dNSName, namelist.get(ix));
            }
            GeneralNames subjectAltName = new GeneralNames(gns);

            PKCS10CertificationRequestBuilder p10Builder =
                            new JcaPKCS10CertificationRequestBuilder(namebuilder.build(), keypair.getPublic());

            ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
            extensionsGenerator.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
            p10Builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());

            PrivateKey pk = keypair.getPrivate();
            JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(
                            pk instanceof ECKey ? EC_SIGNATURE_ALG : SIGNATURE_ALG);
            ContentSigner signer = csBuilder.build(pk);

            csr = p10Builder.build(signer);
        } catch (OperatorCreationException ex) {
            throw new IOException("Could not generate CSR", ex);
        }
    }

    /**
     * Gets the PKCS#10 certification request.
     */
    public PKCS10CertificationRequest getCSR() {
        if (csr == null) {
            throw new IllegalStateException("sign CSR first");
        }

        return csr;
    }

    /**
     * Gets an encoded PKCS#10 certification request.
     */
    public byte[] getEncoded() throws IOException {
        return getCSR().getEncoded();
    }

    /**
     * Writes the signed certificate request to a {@link Writer}.
     *
     * @param w
     *            {@link Writer} to write the PEM file to
     */
    public void write(Writer w) throws IOException {
        if (csr == null) {
            throw new IllegalStateException("sign CSR first");
        }

        try (PemWriter pw = new PemWriter(w)) {
            pw.writeObject(new PemObject("CERTIFICATE REQUEST", getEncoded()));
        }
    }

    /**
     * Writes the signed certificate request to an {@link OutputStream}.
     *
     * @param out
     *            {@link OutputStream} to write the PEM file to
     */
    public void write(OutputStream out) throws IOException {
        write(new OutputStreamWriter(out, "utf-8"));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(namebuilder.build());
        for (String domain : namelist) {
            sb.append(",DNS=").append(domain.toString());
        }
        return sb.toString();
    }

}
