package com.ghostbridge.mapper;

import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AppAiStructuredCommand;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import org.springframework.stereotype.Component;

/**
 * Converts the HTTP request contracts of the AI API into application commands. The requester key is supplied by the caller
 * from the authentication context and never read from a VO. Responses are mapped by AI Web's {@link AiApiMapper}.
 */
@Component
public class AppAiMapper {
    private final AiApiMapper apiMapper;

    public AppAiMapper(AiApiMapper apiMapper) {
        this.apiMapper = apiMapper;
    }

    public AppAiGenerateCommand toCommand(String requesterKey, AiGenerateRequestVO vo) {
        return new AppAiGenerateCommand(requesterKey, vo.promptName(), vo.promptVersion(), vo.variables(), vo.untrustedContent(),
                vo.modelId(), apiMapper.toOptions(vo.options()));
    }

    public AppAiStructuredCommand toCommand(String requesterKey, AiStructuredGenerateRequestVO vo) {
        return new AppAiStructuredCommand(requesterKey, vo.schemaName(), vo.variables(), vo.untrustedContent(),
                apiMapper.toOptions(vo.options()));
    }
}
