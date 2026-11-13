/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.web;

import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigDescription("Embedded HTTP/WebSocket management API configuration for React. Changes apply automatically when web.toml is saved.")
public class WebConfiguration {

  public static final String DEFAULT_APP_URL = "https://react.volmitsoftware.com";
  public static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
      "https://react.volmitsoftware.com",
      "https://react-web-plugin.web.app",
      "https://react-web-plugin.firebaseapp.com"
  );

  @ConfigDoc(value = "Enables the embedded web listener.", impact = "React opens the authenticated API by default. Set false to bind no HTTP or WebSocket listener.")
  private boolean listenerEnabled = true;

  @ConfigDoc(value = "IP address the web listener binds to.", impact = "The default :: wildcard accepts IPv6 and IPv4-mapped traffic for LAN, container, and port-forwarded access. Use a loopback address only behind a same-host reverse proxy or relay.")
  private String listenAddress = "::";

  @ConfigDoc(value = "Preferred TCP port for the web server.", impact = "If this port is occupied, React tries the next 99 ports in order and uses the actual bound port for relay loopback and generated direct URLs when advertisedUrl is empty. Saving web.toml restarts the listener automatically.")
  private int port = 9696;

  @ConfigDoc(value = "Public base URL advertised to direct API clients.", impact = "Set this to the HTTPS URL of a reverse proxy when the listener is exposed beyond localhost. React's embedded listener remains HTTP-only. Changes apply automatically when web.toml is saved.")
  private String advertisedUrl = "";

  @ConfigDoc(value = "Browser origins that may use the direct listener.", impact = "Each entry is an origin: scheme, host and port, with no path; plain http only on loopback. A page from any other origin is refused with origin_not_allowed. Requests without an Origin, such as the relay's, are not affected.")
  private List<String> allowedOrigins = new ArrayList<>(DEFAULT_ALLOWED_ORIGINS);

  @ConfigDoc(value = "Address of React Web that pairing links open.", impact = "Change it to a React Web you host yourself, and add its origin to allowedOrigins.")
  private String appUrl = DEFAULT_APP_URL;

  @ConfigDoc(value = "Name React Web shows for this server.", impact = "Empty uses the first line of the MOTD, or the server folder name when the MOTD is the vanilla default.")
  private String serverName = "";

  @ConfigDoc(value = "Days a pairing lasts without use.", impact = "Each use starts the time again. /react web pair expires= overrides it for one link. 1 to 365.")
  private int tokenDays = 7;

  @ConfigDoc(value = "Requires a valid auth token for read-only HTTP endpoints.", impact = "When true, unauthenticated GET requests are rejected with 401. Set to false only in fully trusted network environments.")
  private boolean requireTokenForReads = true;

  @ConfigDoc(value = "Enables the outbound relay client so browsers can reach this server without an open port.", impact = "React connects out to relayUrl at the first /react web pair, and at each start while a pairing exists. Pairing links then carry the relay address too.")
  private boolean relayEnabled = false;

  @ConfigDoc(value = "Base WSS URL of the relay broker, e.g. wss://relay.arcane.art; the /agent path is appended automatically.", impact = "Only read when relayEnabled is true. Leave empty when relay is disabled.")
  private String relayUrl = "";
}
