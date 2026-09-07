package com.bank.iso20022converter.web;

import com.bank.iso20022converter.transform.ClearingConversionService;
import com.bank.iso20022converter.web.dto.ClearingConversionRequest;
import com.bank.iso20022converter.web.dto.ClearingConversionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-message-type ("clearing") conversions: pain.001 -&gt; pacs.008(+pacs.009), and
 * the pacs.002 -&gt; pain.002 return path. JSON envelope in and out (see
 * {@link ClearingConversionRequest}) because these need structured enrichment input
 * and can produce more than one correlated output message.
 */
@RestController
@RequestMapping("/api/v1/clearing")
public class ClearingController {

    private final ClearingConversionService clearingConversionService;

    public ClearingController(ClearingConversionService clearingConversionService) {
        this.clearingConversionService = clearingConversionService;
    }

    @PostMapping("/convert")
    public ClearingConversionResponse convert(@Valid @RequestBody ClearingConversionRequest request) {
        return clearingConversionService.convert(request);
    }

    /**
     * Same request/response envelope, used for the pacs.002 -&gt; pain.002 return path.
     * No enrichment is needed for that mapping; the field is simply ignored if supplied.
     */
    @PostMapping("/return-status")
    public ClearingConversionResponse returnStatus(@Valid @RequestBody ClearingConversionRequest request) {
        return clearingConversionService.convert(request);
    }
}
