package com.bank.iso20022converter.web.dto;

import com.bank.iso20022converter.transform.MappingNote;

import java.util.List;

public record ClearingConversionResponse(List<GeneratedMessageDto> messages, List<MappingNote> notes) {
}
