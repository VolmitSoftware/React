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
@ConfigDescription("Embedded HTTP/WebSocket management API configuration for React. Changes apply after a React reload or server restart.")
public class WebConfiguration {

  @ConfigDoc(value = "Enables the embedded web listener.", impact = "React opens the authenticated API by default. Set false to bind no HTTP or WebSocket listener.")
  private boolean listenerEnabled = true;

  @ConfigDoc(value = "IP address the web listener binds to.", impact = "The default :: wildcard accepts IPv6 and IPv4-mapped traffic for LAN, container, and port-forwarded access. Use a loopback address only behind a same-host reverse proxy or relay.")
  private String listenAddress = "::";

  @ConfigDoc(value = "Preferred TCP port for the web server.", impact = "If this port is occupied, React tries the next 99 ports in order and uses the actual bound port for relay loopback and generated direct URLs when advertisedUrl is empty. Requires a React reload or server restart to take effect.")
  private int port = 9696;

  @ConfigDoc(value = "Public base URL advertised to direct API clients.", impact = "Set this to the HTTPS URL of a reverse proxy when the listener is exposed beyond localhost. React's embedded listener remains HTTP-only. Requires a React reload or server restart to take effect.")
  private String advertisedUrl = "";

  @ConfigDoc(value = "List of allowed CORS origins for the HTTP API.", impact = "An empty list allows all origins. Add specific origins to restrict cross-origin access.")
  private List<String> corsOrigins = new ArrayList<>();

  @ConfigDoc(value = "Target frequency (Hz) at which sampler updates are pushed over WebSocket connections.", impact = "Higher values increase push fidelity but also network and CPU overhead.")
  private int wsPushHz = 5;

  @ConfigDoc(value = "Requires a valid auth token for read-only HTTP endpoints.", impact = "When true, unauthenticated GET requests are rejected with 401. Set to false only in fully trusted network environments.")
  private boolean requireTokenForReads = true;

  @ConfigDoc(value = "Enables the outbound cloud-relay client for NAT traversal.", impact = "When true, React connects to the relay broker at relayUrl so the companion app can reach servers behind NAT without port forwarding.")
  private boolean relayEnabled = false;

  @ConfigDoc(value = "Base WSS URL of the relay broker, e.g. wss://relay.arcane.art; the /agent path is appended automatically.", impact = "Only read when relayEnabled is true. Leave empty when relay is disabled.")
  private String relayUrl = "";
}
