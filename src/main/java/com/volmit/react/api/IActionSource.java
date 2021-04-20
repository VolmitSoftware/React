package com.volmit.react.api;

public interface IActionSource {
    void sendResponse(String r);

    void sendResponseSuccess(String r);

    void sendResponseError(String r);

    void sendResponseActing(String r);
}
