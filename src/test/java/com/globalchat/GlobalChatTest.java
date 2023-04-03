package com.globalchat;

import com.globalchat.GlobalChatPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GlobalChatTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(GlobalChatPlugin.class);
		RuneLite.main(args);
	}
}