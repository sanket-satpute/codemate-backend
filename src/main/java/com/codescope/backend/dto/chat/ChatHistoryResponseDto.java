package com.codescope.backend.dto.chat;

import com.codescope.backend.dto.BaseResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChatHistoryResponseDto extends BaseResponse<List<ChatMessageDto>> {
    // No additional fields needed, as the data field in BaseResponse will hold the list of ChatMessageDto
    public ChatHistoryResponseDto(boolean success, String message, List<ChatMessageDto> data) {
        super(success, message, data);
    }
}
