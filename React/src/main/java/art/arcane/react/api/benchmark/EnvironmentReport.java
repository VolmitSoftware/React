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

package art.arcane.react.api.benchmark;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public final class EnvironmentReport {
  private final MclogsClient uploader;

  public EnvironmentReport(MclogsClient uploader) {
    this.uploader = Objects.requireNonNull(uploader, "uploader");
  }

  public void upload(CommandSender sender) {
    React plugin = React.instance;
    UploadRequest request = new UploadRequest(plugin, sender,
        plugin.getDescription().getVersion(), Bukkit.getVersion());
    if (!FoliaScheduler.runAsync(plugin, () -> publish(request))) {
      React.warn("Unable to schedule React environment report upload");
      ReactLanguage.send(sender, EnvironmentMessages.UPLOAD_FAILED);
    }
  }

  private void publish(UploadRequest request) {
    try {
      URI url = uploader.publish(create(request.version(), request.serverVersion()),
          "VolmitSoftware - React - v" + request.version(),
          "VolmitSoftware/React/" + request.version());
      reply(request, () -> sendLink(request.sender(), url));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      React.warn("React environment report upload was interrupted", exception);
      reply(request, () -> ReactLanguage.send(request.sender(), EnvironmentMessages.UPLOAD_FAILED));
    } catch (IOException | RuntimeException exception) {
      React.warn("Unable to upload React environment report to mclo.gs", exception);
      reply(request, () -> ReactLanguage.send(request.sender(), EnvironmentMessages.UPLOAD_FAILED));
    }
  }

  private void reply(UploadRequest request, Runnable delivery) {
    if (!request.plugin().isEnabled()) {
      return;
    }
    Runnable guarded = () -> {
      if (request.plugin().isEnabled()) {
        LanguageAudience.run(request.sender() instanceof Player player ? player.getUniqueId() : null, delivery);
      }
    };
    boolean scheduled = request.sender() instanceof Player player
        ? FoliaScheduler.runEntity(request.plugin(), player, guarded)
        : FoliaScheduler.runGlobal(request.plugin(), guarded);
    if (!scheduled && request.plugin().isEnabled()) {
      React.warn("Unable to schedule React environment report feedback");
    }
  }

  private void sendLink(CommandSender sender, URI url) {
    Component message = ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK)
        .clickEvent(ClickEvent.openUrl(url.toString()));
    ComponentMessenger.send(sender, ComponentText.component(message));
  }

  private String create(String version, String serverVersion) {
    StringBuilder sb = new StringBuilder();
    sb.append(" -- == React Info == -- \n");
    sb.append("React Version: ").append(version).append("\n");
    sb.append("Server Type: ").append(serverVersion).append("\n");
    sb.append(" -- == Platform Overview == -- \n");
    sb.append("Version: ").append(Platform.getVersion()).append(" - Platform: ").append(Platform.getName()).append("\n");
    sb.append("Java Vendor: ").append(Platform.ENVIRONMENT.getJavaVendor()).append(" - Java Version: ").append(Platform.ENVIRONMENT.getJavaVersion()).append("\n");
    sb.append(" -- == Storage Information == -- \n");
    sb.append("Total Space: ").append(Form.memSize(Platform.STORAGE.getTotalSpace())).append("\n");
    sb.append("Free Space: ").append(Form.memSize(Platform.STORAGE.getFreeSpace())).append("\n");
    sb.append("Used Space: ").append(Form.memSize(Platform.STORAGE.getUsedSpace())).append("\n");
    sb.append(" -- == Memory Information == -- \n");
    sb.append("Physical Memory - Total: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory())).append("\n");
    sb.append("Virtual Memory - Total: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory())).append("\n");
    sb.append(" -- == CPU Overview == -- \n");
    sb.append("CPU Architecture: ").append(Platform.CPU.getArchitecture()).append(" Available Processors: ").append(Platform.CPU.getAvailableProcessors()).append("\n");
    sb.append("CPU Load: ").append(Form.pc(Platform.CPU.getCPULoad())).append(" CPU Live Process Load: ").append(Form.pc(Platform.CPU.getLiveProcessCPULoad())).append("\n");

    return sb.toString();
  }

  private record UploadRequest(React plugin, CommandSender sender, String version, String serverVersion) {
  }
}
