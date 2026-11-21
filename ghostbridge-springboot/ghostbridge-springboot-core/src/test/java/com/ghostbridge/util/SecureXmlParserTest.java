package com.ghostbridge.util;

import com.ghostbridge.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureXmlParserTest {

    private static ByteArrayInputStream xml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesValidXml() {
        var doc = SecureXmlParser.parse(xml("<definitions><service name=\"x\"/></definitions>"), 1024);
        assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("definitions");
    }

    @Test
    void rejectsExternalEntity() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";
        assertThatThrownBy(() -> SecureXmlParser.parse(xml(xxe), 4096)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsEntityExpansionBomb() {
        String bomb = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;\">]><l>&b;</l>";
        assertThatThrownBy(() -> SecureXmlParser.parse(xml(bomb), 4096)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsOversizedDocument() {
        String big = "<a>" + "x".repeat(2000) + "</a>";
        assertThatThrownBy(() -> SecureXmlParser.parse(xml(big), 100)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> SecureXmlParser.parse(xml("<a><b></a>"), 100)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void secureStaxFactoryDisablesDtd() {
        var factory = SecureXmlParser.newSecureXmlInputFactory();
        assertThat(factory.getProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD)).isEqualTo(false);
    }
}
