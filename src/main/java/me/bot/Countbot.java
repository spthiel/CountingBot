package me.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.common.ReactorResources;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.gateway.GatewayClient;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

import me.database.Database;
import me.main.Config;
import me.utils.FileUtils;

public class Countbot {
	
	public Countbot() {
		
		ObjectMapper mapper = new ObjectMapper();
		var configPath = Paths.get(FileUtils.getJarPath().getParent().toString(), "config.json");
		var configFile = configPath.toFile();
		if (!configFile.exists()) {
			try {
				System.out.println("Creating config file");
				Files.copy(getClass().getResourceAsStream("/config.json"), configPath);
				System.out.println("Successfully created default config at " + configPath.toString());
				System.exit(0);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		try {
			Config.instance = mapper.readValue(configFile, Config.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void start() {
		var client = DiscordClient.builder(Config.instance.TOKEN)
			.setReactorResources(ReactorResources.builder()
									 .httpClient(HttpClient.create().compress(true).keepAlive(false).followRedirect(true).secure())
												 .build())
			.build();
		
		client.gateway().setEnabledIntents(IntentSet.of(Intent.GUILD_MESSAGES));
		
		var gatewayClient = client.login().block();
		new Listener(gatewayClient);
		gatewayClient.onDisconnect().block();
	}
	
}
