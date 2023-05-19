package com.volmit.react.api.benchmark;

import com.volmit.react.React;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.plugin.VolmitSender;
import com.volmit.react.util.reflect.Platform;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Hastebin {

    public static void enviornment(VolmitSender sender) {
        // Construct the server information
        StringBuilder sb = new StringBuilder();
        sb.append(" -- == React Info == -- \n");
        sb.append("React Version Version: ").append(React.instance.getDescription().getVersion()).append("\n");
        sb.append("Server Type: ").append(Bukkit.getVersion()).append("\n");
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

        // Upload the server information to Hastebin
        try {
            String hastebinUrl = uploadToHastebin(sb.toString());

            // Create the clickable message
            TextComponent message = new TextComponent(C.AQUA + "[Link]");
            TextComponent link = new TextComponent(C.DARK_AQUA + hastebinUrl);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, hastebinUrl));
            message.addExtra(link);

            // Send the clickable message to the player
            sender.spigot().sendMessage(message);
        } catch (Exception e) {
            // Error occurred during upload
            sender.sendMessage(C.DARK_RED + "Failed to upload server information to Hastebin.");
        }
    }

    private static String uploadToHastebin(String content) throws Exception {
        URL url = new URL("https://paste.bytecode.ninja/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setDoOutput(true);

        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(content);
        wr.flush();
        wr.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String response = br.readLine();
        br.close();

        return "https://paste.bytecode.ninja/" + response.split("\"")[3];
    }



}
