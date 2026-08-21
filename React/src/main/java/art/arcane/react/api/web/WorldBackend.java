package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.WorldDto;

import java.util.List;

public interface WorldBackend {
    List<WorldDto> list();

    WorldDto update(String worldKey, Double budgetMs, Double panicMs, Double releaseMs);
}
