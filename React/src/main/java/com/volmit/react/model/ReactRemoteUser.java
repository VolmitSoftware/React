package com.volmit.react.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReactRemoteUser {
    private String password;
    @Builder.Default
    private boolean actions = false;
    @Builder.Default
    private boolean sample = true;
    @Builder.Default
    private boolean console = false;
    @Builder.Default
    private boolean files = false; 
}
