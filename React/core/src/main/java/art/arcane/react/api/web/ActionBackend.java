package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.ActionDescriptorDto;

import java.util.List;

public interface ActionBackend {
    List<ActionDescriptorDto> list();

    ActionDescriptorDto get(String id);
}
