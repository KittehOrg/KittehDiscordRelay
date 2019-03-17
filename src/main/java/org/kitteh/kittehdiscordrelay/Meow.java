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
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import net.engio.mbassy.listener.Handler;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.util.Format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

    private Config config;
    private Client ircClient;
    private JDA jda;

    public static void main(String[] args) {
        Meow meow = new Meow();
        meow.start();
    }

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
    }

    @SubscribeEvent
    public void discordMessage(MessageReceivedEvent event) {
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
        String senderName = event.getMember().getEffectiveName();
        String sendingName = senderName.length() > 1 ? (senderName.charAt(0) + "\u200B" + senderName.substring(1)) : senderName;
        Format color = COLORS[(int) ((event.getAuthor().getIdLong() / 100L) % COLORS.length)];
        target.ifPresent(ch -> ircClient.sendMessage(ch, "<" + color.toString() + sendingName + Format.RESET.toString() + "> " + event.getMessage().getContentDisplay()));
    }

    @Handler
    public void ircMessage(ChannelMessageEvent event) {
        if (event.getActor().getNick().equalsIgnoreCase(event.getClient().getNick())) {
            return; // It's just me
        }
        OptionalLong target = this.config.getDiscordIdByIrcChannel(event.getChannel().getName());
        target.ifPresent(id -> jda.getTextChannelById(id).sendMessage("<" + event.getActor().getNick() + "> " + event.getMessage()).queue());
    }
}
