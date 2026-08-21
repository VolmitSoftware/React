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
@ConfigDescription("Embedded HTTP/WebSocket management API configuration for React.")
public class WebConfiguration {

  @ConfigDoc(value = "Enables the embedded web server.", impact = "When false, no HTTP or WebSocket listeners are bound. All other settings in this section are ignored.")
  private boolean enabled = false;

  @ConfigDoc(value = "IP address the web server binds to.", impact = "Use 127.0.0.1 to restrict access to localhost. Set to 0.0.0.0 to accept external connections.")
  private String bindAddress = "127.0.0.1";

  @ConfigDoc(value = "TCP port the web server listens on.", impact = "Ensure this port is not already in use. Requires a server restart to take effect.")
  private int port = 9696;

  @ConfigDoc(value = "Public base URL advertised to direct API clients.", impact = "Set this to the HTTPS URL of a reverse proxy when the listener is exposed beyond localhost. React's embedded listener remains HTTP-only.")
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
