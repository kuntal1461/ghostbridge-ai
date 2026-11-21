package com.ghostbridge.util;

import com.ghostbridge.config.GhostBridgeProperties;
import com.ghostbridge.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {
    private final UploadValidator validator = new UploadValidator(
            new GhostBridgeProperties.Upload(DataSize.ofKilobytes(1), DataSize.ofKilobytes(2), List.of("application/xml", "text/xml")));

    @Test
    void acceptsValidUpload() {
        assertThatCode(() -> validator.validate("service.wsdl", "application/xml; charset=UTF-8", 100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooLarge() {
        assertThatThrownBy(() -> validator.validate("a.xml", "text/xml", 2048)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> validator.validate("a.xml", "text/xml", 0)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsDisallowedContentType() {
        assertThatThrownBy(() -> validator.validate("a.xml", "application/zip", 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> validator.validate("a.xml", null, 10)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsPathTraversalAndBadNames() {
        assertThatThrownBy(() -> validator.validate("../etc/passwd", "text/xml", 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> validator.validate("a\\b.xml", "text/xml", 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> validator.validate("", "text/xml", 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> validator.validate(null, "text/xml", 10)).isInstanceOf(InvalidInputException.class);
    }
}
