package me.database;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.discordjson.json.WebhookData;
import discord4j.rest.RestClient;
import discord4j.rest.RestClientBuilder;
import reactor.core.publisher.Mono;

import java.sql.SQLException;
import java.util.HashMap;

public class ChannelCache {
	
	public        HashMap<Long, Channel> channels;
	private final GatewayDiscordClient   gateway;
	
	public ChannelCache(GatewayDiscordClient gateway) {
		
		this.gateway = gateway;
		
		channels = new HashMap<>();
		var db = Database.getInstance();
		try {
			var result = db.query("SELECT * FROM channels");
			while (result.next()) {
				long   id            = result.getLong("channelid");
				long   count         = result.getLong("count");
				long   webhookId     = result.getLong("webhookid");
				String webhookSecret = result.getString("secret");
				channels.put(id, new Channel(id, count, webhookId, webhookSecret));
			}
			db.clean(result);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Channel getChannel(Snowflake channel) {
		
		return channels.get(channel.asLong());
	}
	
	public boolean contains(Snowflake channel) {
		
		return channels.containsKey(channel.asLong());
	}
	
	public Mono<TextChannel> remove(TextChannel channel) {
		
		return remove(channel.getId()).map(ignored -> channel);
	}
	
	public Mono<Snowflake> remove(Snowflake id) {
		
		var cache = this.getChannel(id);
		return cache.delete("Channel removed from counting bot", true)
					.map(ignored -> {
						try {
							var statement = Database.getInstance().createStatement("DELETE FROM channels WHERE channelid = ?");
							statement.setLong(1, id.asLong());
							Database.getInstance().execute(statement);
							channels.remove(id.asLong());
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
						return id;
					});
	}
	
	public Mono<TextChannel> add(TextChannel channel) {
		
		return channel.createWebhook("Counting message delete hook")
					  .map(webhook -> {
						  if (webhook.getToken().isEmpty()) {
							  throw new RuntimeException("Invalid hook, secret is empty");
						  }
			
						  var query = "INSERT INTO channels (channelid, webhookid, secret) VALUES (?, ?, ?)";
						  try {
							  var statement = Database.getInstance().createStatement(query);
							  statement.setLong(1, channel.getId().asLong());
							  statement.setLong(2, webhook.getId().asLong());
							  statement.setString(3, webhook.getToken().get());
							  Database.getInstance().execute(statement);
							  channels.put(
								  channel.getId().asLong(),
								  new Channel(
									  channel.getId().asLong(), 0,
									  webhook.getId().asLong(),
									  webhook.getToken().get()
								  )
							  );
						  } catch (SQLException e) {
							  webhook.delete("Failed to store channel to database").subscribe();
							  throw new RuntimeException(e);
						  }
						  return channel;
					  });
	}
	
	public long getCount(Snowflake channel) {
		
		return getChannel(channel).count;
	}
	
	public void increment(Snowflake channel) {
		
		getChannel(channel).increment();
	}
	
	public void reset(Snowflake channel) {
		
		getChannel(channel).reset();
	}
	
	public void set(Snowflake channel, long value) {
		
		getChannel(channel).set(value);
	}
	
	
	public Snowflake getLastSender(Snowflake channel) {
		
		return getChannel(channel).getLastSender();
	}
	
	public void setLastSender(Snowflake channel, Snowflake lastSender) {
		getChannel(channel).setLastSender(lastSender);
	}
	
	public Mono<Void> sendToChannel(Snowflake channel, WebhookExecuteSpec spec) {
		
		return getChannel(channel).sendMessage(spec);
	}
	
	private class Channel {
		
		private       long   count;
		private final long   webhookId;
		private final String webhookSecret;
		private final long   channelid;
		private Snowflake lastSender = null;
		
		Channel(long channelid, long count, long webhookId, String webhookSecret) {
			
			this.channelid = channelid;
			this.count = count;
			this.webhookId = webhookId;
			this.webhookSecret = webhookSecret;
		}
		
		private Mono<Webhook> getWebhook() {
			
			return gateway.getWebhookByIdWithToken(Snowflake.of(webhookId), webhookSecret);
		}
		
		private void persist() {
			
			var query = "UPDATE channels SET count = ? WHERE channelid = ?";
			try {
				var statement = Database.getInstance().createStatement(query);
				statement.setLong(1, count);
				statement.setLong(2, channelid);
				Database.getInstance().execute(statement);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		
		public void reset() {
			
			this.count = 0;
			persist();
		}
		
		public void increment() {
			
			this.count++;
			persist();
		}
		
		public void set(long count) {
			
			this.count = count;
			persist();
		}
		
		public Snowflake getLastSender() {
			
			return lastSender;
		}
		
		public void setLastSender(Snowflake lastSender) {
			
			this.lastSender = lastSender;
		}
		
		private <V> Mono<V> delete(String reason, V next) {
			
			return getWebhook().flatMap(webhook -> webhook.delete(reason))
							   .map(ignored -> next)
							   .switchIfEmpty(Mono.just(next));
		}
		
		public Mono<Void> sendMessage(WebhookExecuteSpec webhookExecuteSpec) {
			
			return getWebhook().flatMap(webhook -> webhook.execute(webhookExecuteSpec));
		}
	}
	
}
