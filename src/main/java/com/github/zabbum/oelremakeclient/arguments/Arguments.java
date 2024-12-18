package com.github.zabbum.oelremakeclient.arguments;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Arguments {
    @Builder.Default
    private Boolean devMode = false;
    @Builder.Default
    private String serverAddress = "localhost:8080";
    @Builder.Default
    private String lang = "pl-PL";
}
