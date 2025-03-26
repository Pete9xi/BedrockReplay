/*
 * Copyright (c) 2023 brokiem
 * This project is licensed under the MIT License
 */

package com.brokiem.bedrockreplay.bedrock.server;

import com.alibaba.fastjson.JSONObject;
import com.brokiem.bedrockreplay.bedrock.network.handler.upstream.UpstreamPacketHandler;
import com.brokiem.bedrockreplay.bedrock.player.ProxiedPlayer;
import com.brokiem.bedrockreplay.utils.FileManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.Getter;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

import java.net.InetSocketAddress;

public class ProxyServer {

    @Getter
    private static ProxyServer instance;

    private boolean isRunning = false;
    private Channel server;

    public static final BedrockCodec BEDROCK_CODEC = Bedrock_v786.CODEC.toBuilder()
        .helper(() -> {
            var helper = Bedrock_v786.CODEC.createHelper();
            helper.setEncodingSettings(EncodingSettings.builder()
                .maxListSize(Integer.MAX_VALUE)
                .maxByteArraySize(Integer.MAX_VALUE)
                .maxNetworkNBTSize(Integer.MAX_VALUE)
                .maxItemNBTSize(Integer.MAX_VALUE)
                .maxStringLength(Integer.MAX_VALUE)
                .build());
            return helper;
        })
        .build();

    private final InetSocketAddress bindAddress;

    @Getter
    private final String downstreamAddress;
    @Getter
    private final int downstreamPort;

    public ProxyServer(InetSocketAddress address) {
        bindAddress = address;

        JSONObject config = JSONObject.parseObject(FileManager.getFileContents("config.json"));
        assert config != null;
        downstreamAddress = config.getJSONObject("server").getString("address");
        downstreamPort = config.getJSONObject("server").getIntValue("port");

        instance = this;
    }

    public void start() {
        if (isRunning) {
            return;
        }

        BedrockPong pong = new BedrockPong()
                .edition("MCPE")
                .motd("BedrockReplay")
                .subMotd("Proxy Server")
                .playerCount(0)
                .maximumPlayerCount(1)
                .gameType("Survival")
                .protocolVersion(BEDROCK_CODEC.getProtocolVersion())
                .ipv4Port(bindAddress.getPort())
                .ipv6Port(bindAddress.getPort())
                .version(BEDROCK_CODEC.getMinecraftVersion());

        this.server = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
                .option(RakChannelOption.RAK_IP_DONT_FRAGMENT, true)
                .option(RakChannelOption.RAK_MTU_SIZES, new Integer[]{1492, 1200, 576})
                .group(new NioEventLoopGroup())
                .childHandler(new BedrockServerInitializer() {
                    @Override
                    protected void initSession(BedrockServerSession session) {
                        ProxiedPlayer proxiedPlayer = new ProxiedPlayer(session);

                        session.setCodec(BEDROCK_CODEC);
                        session.setPacketHandler(new UpstreamPacketHandler(proxiedPlayer));
                    }
                })
                .bind(bindAddress)
                .syncUninterruptibly()
                .channel();
                 this.server.pipeline().remove(RakServerRateLimiter.NAME);

        isRunning = true;
    }
}
