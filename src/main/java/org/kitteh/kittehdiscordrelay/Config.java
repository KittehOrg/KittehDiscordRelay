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

import com.google.gson.GsonBuilder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class Config {
    public static class Irc {
        public String host = "localhost";
        public String password;
        public int port = 6697;
        public boolean secure = true;
        public String nick = "KittehRelay";
        public String user = "meow";
        public String realName = "Kitteh Discord Relay";
    }

    public String discordToken = "PUT TOKEN HERE";
    public Irc irc = new Irc();
    public Map<String, Long> links = new HashMap<>();

    public Optional<String> getIrcChannelByDiscord(long id) {
        return this.links.entrySet().stream().filter(e -> e.getValue() == id).map(Map.Entry::getKey).findFirst();
    }

    public OptionalLong getDiscordIdByIrcChannel(@NonNull String channel) {
        return this.links.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(channel)).mapToLong(Map.Entry::getValue).findFirst();
    }

    // This is just a cute little helper to make yourself a fresh config
    public static void main(String[] args) {
        Config conf = new Config();
        conf.links.put("#channel", 1111111111111111111L);
        System.out.println(new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(conf));
    }
}
