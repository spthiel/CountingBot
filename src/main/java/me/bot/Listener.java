package me.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.object.entity.GuildEmoji;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.discordjson.json.EmojiData;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;

import me.database.ChannelCache;
import me.utils.MathUtils;

public class Listener {
	
	private final GatewayDiscordClient gateway;
	private final ChannelCache         channels;
	
	public Listener(GatewayDiscordClient gateway) {
		
		this.gateway = gateway;
		
		gateway.on(MessageCreateEvent.class, (event) -> {
				   onMessage(event);
				   return Mono.empty();
			   })
			   .subscribe();
		
		gateway.on(MessageDeleteEvent.class, (event) -> {
				   onDelete(event);
				   return Mono.empty();
			   })
			   .subscribe();
		
		channels = new ChannelCache(gateway);
	}
	
	public void onMessage(MessageCreateEvent event) {
		
		var message = event.getMessage();
		
		if (message.getAuthor()
				   .isEmpty() || message.getGuildId()
										.isEmpty()) {
			return;
		}
		
		var channelId = message.getChannelId();
		var author = message.getAuthor()
							.get();
		
		if (author.isBot()) {
			return;
		}
		
		var present = this.channels.contains(channelId);
		if (message.getUserMentionIds()
				   .stream()
				   .map(Snowflake :: asLong)
				   .anyMatch(id -> id == gateway.getSelfId()
												.asLong())) {
			var content = message.getContent();
			content = content.substring(content.indexOf('>') + 1)
							 .trim()
							 .toLowerCase();
			var args = content.split(" +");
			
			author.asMember(message.getGuildId()
								   .get())
				  .flatMap(this :: hasPermissions)
				  .filter(value -> value)
				  .flatMap((ignored) -> {
					  return message.getChannel()
									.cast(TextChannel.class)
									.doOnNext(textchannel -> this.processCommands(args, message, textchannel, present));
				  })
				  .subscribe();
			return;
		}
		
		if (!present) {
			return;
		}
		
		
		var currentCount = channels.getCount(channelId);
		var content    = message.getContent();
		var lastSender = channels.getLastSender(channelId);
		double value;
		try {
			value = MathUtils.eval(content);
		} catch (Exception e) {
			value = 0;
		}
		
		if (value == currentCount + 1 && (lastSender == null || !lastSender.equals(author.getId()))) {
			channels.increment(channelId);
			channels.setLastSender(channelId, author.getId());
			message.addReaction(ReactionEmoji.unicode("\u2705")).subscribe();
		} else if (currentCount <= 3) {
			message.addReaction(ReactionEmoji.custom(Snowflake.of(398120014974287873L), "red_cross", false))
				   .then(message.addReaction(ReactionEmoji.unicode("\uD83D\uDCAC")))
				   .subscribe();
		} else {
			channels.reset(channelId);
			channels.setLastSender(channelId, null);
			
			var response = MessageCreateSpec.builder()
											.allowedMentions(AllowedMentions.suppressEveryone())
											.content(String.format("%s RUINED IT AT %d!!", author.getMention(), currentCount + 1))
											.build();
			
			message.getChannel()
						  .flatMap(channel -> channel.createMessage(response))
						  .flatMap(ignored -> message.addReaction(ReactionEmoji.custom(Snowflake.of(398120014974287873L), "red_cross", false)))
				.subscribe();
		}
		
	}
	
	private void processCommands(String[] args, Message message, TextChannel channel, boolean present) {
		
		switch (args[0]) {
			case "toggle":
				var text = "Successfully %s this channel %s counting channels";
				Mono.just(channel)
						.flatMap(present ? channels::remove : channels::add)
						.flatMap(ignored -> channel.createMessage(String.format(text, present ? "removed" : "added", present ? "from" : "to")))
						.subscribe();
				break;
			case "debug":
				if (args.length == 1) {
					return;
				}
				text = "Message content `%s` evaluates to %.3f";
				channel.getMessageById(Snowflake.of(args[1]))
					   .map(Message :: getContent)
					   .map(content -> {
						   try {
							   var value = MathUtils.eval(content);
							   return String.format(text, content.replace("`", ""), value);
						   } catch (Exception e) {
							   return e.getClass()
									   .getName() + ": " + e.getMessage();
						   }
					   })
					   .onErrorReturn("Invalid message id")
					   .flatMap(content -> channel.createMessage(MessageCreateSpec.builder()
																				  .allowedMentions(AllowedMentions.suppressAll())
																				  .content(content)
																				  .build()))
					   .subscribe();
				break;
			case "set":
				if (!present || args.length == 1) {
					return;
				}
				text = "Successfully set count to %d";
				Mono.just(args[1])
					.map(Long :: parseLong)
					.map(val -> {
						channels.set(channel.getId(), val);
						return String.format(text, val);
					})
					.onErrorReturn("Invalid number")
					.flatMap(content -> channel.createMessage(MessageCreateSpec.builder()
																			   .allowedMentions(AllowedMentions.suppressAll())
																			   .content(content)
																			   .build()))
					.subscribe();
				break;
		}
	}
	
	private Mono<Boolean> hasPermissions(Member author) {
		
		return author.getBasePermissions()
					 .map(permissions -> permissions.contains(Permission.ADMINISTRATOR) || permissions.contains(Permission.MANAGE_GUILD));
	}
	
	private void onDelete(MessageDeleteEvent event) {
		
		if (event.getMessage()
				 .isEmpty()) {
			return;
		}
		var message = event.getMessage()
						   .get();
		
		if (!channels.contains(event.getChannelId())) {
			return;
		}
		
		if (message.getContent()
				   .equalsIgnoreCase("")) {
			return;
		}
		
		if (message.getAuthor()
				   .isEmpty()) {
			return;
		}
		
		var author = message.getAuthor()
							.get();
		
		event.getChannel()
			 .subscribe(channel -> {
				 if (channel.getLastMessageId()
							.isPresent() &&
					 channel.getLastMessageId()
							.get()
							.asLong() > message.getId()
											   .asLong()) {
					 return;
				 }
			
				 var spec = WebhookExecuteSpec.builder()
											  .avatarUrl(author.getAvatarUrl())
											  .content(message.getContent())
											  .username(author.getUsername())
											  .build();
			
				 channels.sendToChannel(channel.getId(), spec)
						 .subscribe();
			
			 });
	}
	
}
