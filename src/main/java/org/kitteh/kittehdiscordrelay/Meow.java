/*
 * * Copyright (C) 2019 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.kittehdiscordrelay;

import com.google.gson.Gson;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookClientBuilder;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;
import net.engio.mbassy.listener.Handler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.util.Cutter;
import org.kitteh.irc.client.library.util.Format;
import org.kitteh.pastegg.PasteBuilder;
import org.kitteh.pastegg.PasteContent;
import org.kitteh.pastegg.PasteFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class Meow extends Thread {
    private static Format[] COLORS = {
            Format.BLUE,
            Format.BROWN,
            Format.CYAN,
            Format.DARK_GREEN,
            Format.GREEN,
            Format.MAGENTA,
            Format.OLIVE,
            Format.PURPLE,
            Format.RED,
            Format.TEAL,
            Format.YELLOW,
    };

    private static final Cutter CUTTER = new Cutter.DefaultWordCutter();

    public static void main(String[] args) {
        Meow meow = new Meow();
        meow.start();
    }

    private Config config;
    private Client ircClient;
    private JDA jda;
    private Map<Long, WebhookClient> hooks = new HashMap<>();

    @Override
    public void run() {
        try {
            this.config = new Gson().fromJson(new FileReader(new File("config.json")), Config.class);
        } catch (FileNotFoundException e) {
            System.out.println("Oh no, no config file");
            System.exit(66);
        }

        this.ircClient = Client.builder()
                .nick(this.config.irc.nick)
                .realName(this.config.irc.realName)
                .user(this.config.irc.user)
                .server()
                .host(this.config.irc.host)
                .secure(this.config.irc.secure)
                .port(this.config.irc.port)
                .password(this.config.irc.password)
                .then()
                .build();

        this.ircClient.getEventManager().registerEventListener(this);
        this.config.links.keySet().forEach(this.ircClient::addChannel);
        this.ircClient.connect();

        try {
            this.jda = new JDABuilder(this.config.discordToken)
                    .setEventManager(new AnnotatedEventManager())
                    .addEventListener(this)
                    .build();
        } catch (Exception e) {
            System.out.println("Oh no, JDA fell over");
            e.printStackTrace();
            System.exit(66);
        }
        for (Map.Entry<Long, String> e : this.config.getHooks().entrySet()) {
            this.hooks.put(e.getKey(), new WebhookClientBuilder(e.getValue()).build());
        }
    }

    @SubscribeEvent
    public void discordMessage(MessageReceivedEvent event) {
        if (event.getAuthor().getName().endsWith("(on IRC)")) {
            return;
        }
        if (event.getAuthor().getIdLong() == this.jda.getSelfUser().getIdLong()) {
            return; // It's just me
        }
        Optional<String> target = this.config.getIrcChannelByDiscord(event.getChannel().getIdLong());
        if (!target.isPresent()) {
            return; // Not a channel receiving messages
        }
        Optional<Channel> targetChannel = this.ircClient.getChannel(target.get());
        if (!targetChannel.isPresent()) {
            return; // Not present in channel. TODO probably should complain about this somewhere
        }

        String senderName;

        if (event.isWebhookMessage()) {
            senderName = event.getAuthor().getName();
        } else {
            senderName = event.getMember().getEffectiveName();
        }

        String sendingName = senderName.length() > 1 ? (senderName.charAt(0) + "\u200B" + senderName.substring(1)) : senderName;
        Format color = COLORS[(int) ((event.getAuthor().getIdLong() / 100L) % COLORS.length)];
        String prefix = (event.isWebhookMessage() ? "<WEB " : "<") + color.toString() + sendingName + Format.RESET.toString() + "> ";
        List<String> messages = this.splitify("PRIVMSG" + prefix, target.get(), event.getMessage().getContentDisplay());
        List<String> attachments = new ArrayList<>();
        event.getMessage().getAttachments().forEach(a -> attachments.add(a.getUrl()));
        messages.addAll(attachments);
        if ((messages.size()) < 3) {
            messages.forEach(m -> this.ircClient.sendMessage(target.get(), prefix + m));
        } else {
            PasteBuilder builder = new PasteBuilder()
                    .name((event.isWebhookMessage() ? "WEB" : "") + senderName + " in #" + event.getChannel().getName() + " on " + event.getGuild().getName())
                    .addFile(new PasteFile("message.md", new PasteContent(PasteContent.ContentType.TEXT, event.getMessage().getContentRaw().replaceAll("[\\r\\n]+", "  \n"))));
            if (!attachments.isEmpty()) {
                for (int i = 0; i < attachments.size(); i++) {
                    builder.addFile(new PasteFile("attachment" + i + ".md", new PasteContent(PasteContent.ContentType.TEXT, "[" + attachments.get(i) + "](" + attachments.get(i) + ")")));
                }
            }
            PasteBuilder.PasteResult result = builder.build();
            if (result.getPaste().isPresent()) {
                this.ircClient.sendMessage(target.get(), prefix + "https://paste.gg/" + result.getPaste().get().getId());
            } else {
                this.ircClient.sendMessage(target.get(), color.toString() + sendingName + Format.RESET.toString() + " sent too much, but the paste service is offline");
            }
        }
    }

    @Handler
    public void ircMessage(ChannelMessageEvent event) {
        if (event.getActor().getNick().equalsIgnoreCase(event.getClient().getNick())) {
            return; // It's just me
        }
        OptionalLong target = this.config.getDiscordIdByIrcChannel(event.getChannel().getName());

        target.ifPresent(id -> {
            TextChannel channel = jda.getTextChannelById(id);
            WebhookClient client = this.hooks.get(id);
            if (client == null) {
                channel.sendMessage("<" + event.getActor().getNick() + "> " + event.getMessage()).queue();
                return;
            }
            WebhookMessageBuilder builder = new WebhookMessageBuilder()
                    .setUsername(event.getActor().getNick() + " (on IRC)")
                    .setContent(event.getMessage());
            List<Member> mem = new ArrayList<>(channel.getGuild().getMembersByName(event.getActor().getNick(), false));
            mem.addAll(channel.getGuild().getMembersByEffectiveName(event.getActor().getNick(), false));
            if (!mem.isEmpty()) {
                builder.setAvatarUrl(mem.get(0).getUser().getAvatarUrl());
            }
            client.send(builder.build());
        });
    }

    private List<String> splitify(String guaranteedLengthBits, String target, String message) {
        List<String> list = new ArrayList<>();
        for (String split : message.split("[\\r\\n]+")) {
            list.addAll(CUTTER.split(split, this.getRemainingLength(guaranteedLengthBits, target)));
        }
        return list;
    }

    // Borrowed from KICL with (self) permission
    private int getRemainingLength(@NonNull String type, @NonNull String target) {
        // :nick!name@host PRIVMSG/NOTICE TARGET :MESSAGE\r\n
        // So that's two colons, three spaces, CR, and LF. 7 chars.
        // 512 - 7 = 505
        // Then, drop the user's full name (nick!name@host) and target
        // If self name is unknown, let's just do 100 for now
        // This will only happen for messages prior to getting a self WHOIS
        // Lastly drop the PRIVMSG or NOTICE length
        return 505 - this.ircClient.getUser().map(user -> user.getName().length()).orElse(100) - target.length() - type.length();
    }
}
