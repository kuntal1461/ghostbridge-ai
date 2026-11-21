package com.ghostbridge.util;

import com.ghostbridge.exception.InvalidInputException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Parses untrusted XML (WSDL, SOAP) safely: DOCTYPE and external entities are disabled
 * (blocks XXE and entity-expansion attacks) and the document size is capped.
 */
public final class SecureXmlParser {

    private SecureXmlParser() {
    }

    public static Document parse(InputStream input, long maxBytes) {
        try (InputStream bounded = new BoundedInputStream(input, maxBytes)) {
            return newSecureFactory().newDocumentBuilder().parse(bounded);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Secure XML parser could not be configured", e);
        } catch (SAXException | IOException e) {
            throw new InvalidInputException("Invalid or unsafe XML document", e);
        }
    }

    /** Hardened factory; reuse it for StAX-based parsing. */
    public static XMLInputFactory newSecureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static DocumentBuilderFactory newSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long limit;
        private long read;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > limit) {
                throw new InvalidInputException("XML document exceeds the maximum allowed size");
            }
        }
    }
}
