package com.velocitypowered.proxy.network;

import com.google.common.base.Preconditions;
import com.velocitypowered.natives.util.Natives;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.protocol.netty.GS4QueryHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ConnectionManager {

  private static final WriteBufferWaterMark SERVER_WRITE_MARK = new WriteBufferWaterMark(1 << 21,
      1 << 21);
  private static final Logger LOGGER = LogManager.getLogger(ConnectionManager.class);
  private final Map<InetSocketAddress, Channel> endpoints = new HashMap<>();
  private final TransportType transportType;
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final VelocityServer server;
  // This is intentionally made public for plugins like ViaVersion, which inject their own
  // protocol logic into the proxy.
  @SuppressWarnings("WeakerAccess")
  public final ServerChannelInitializerHolder serverChannelInitializer;

  private final DnsAddressResolverGroup resolverGroup;

  /**
   * Initalizes the {@code ConnectionManager}.
   *
   * @param server a reference to the Velocity server
   */
  public ConnectionManager(VelocityServer server) {
    this.server = server;
    this.transportType = TransportType.bestType();
    this.bossGroup = this.transportType.createEventLoopGroup(TransportType.Type.BOSS);
    this.workerGroup = this.transportType.createEventLoopGroup(TransportType.Type.WORKER);
    this.serverChannelInitializer = new ServerChannelInitializerHolder(
        new ServerChannelInitializer(this.server));
    this.resolverGroup = new DnsAddressResolverGroup(
        new DnsNameResolverBuilder()
            .channelType(this.transportType.datagramChannelClass)
            .negativeTtl(15)
            .ndots(1)
    );
  }

  public void logChannelInformation() {
    LOGGER.info("Connections will use {} channels, {} compression, {} ciphers", this.transportType,
        Natives.compress.getLoadedVariant(), Natives.cipher.getLoadedVariant());
  }

  /**
   * Binds a Minecraft listener to the specified {@code address}.
   *
   * @param address the address to bind to
   */
  public void bind(final InetSocketAddress address) {
    final ServerBootstrap bootstrap = new ServerBootstrap()
        .channel(this.transportType.serverSocketChannelClass)
        .group(this.bossGroup, this.workerGroup)
        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, SERVER_WRITE_MARK)
        .childHandler(this.serverChannelInitializer.get())
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.IP_TOS, 0x18)
        .localAddress(address);
    bootstrap.bind()
        .addListener((ChannelFutureListener) future -> {
          final Channel channel = future.channel();
          if (future.isSuccess()) {
            this.endpoints.put(address, channel);
            LOGGER.info("Listening on {}", channel.localAddress());
          } else {
            LOGGER.error("Can't bind to {}", address, future.cause());
          }
        });
  }

  /**
   * Binds a GS4 listener to the specified {@code hostname} and {@code port}.
   *
   * @param hostname the hostname to bind to
   * @param port the port to bind to
   */
  public void queryBind(final String hostname, final int port) {
    InetSocketAddress address = new InetSocketAddress(hostname, port);
    final Bootstrap bootstrap = new Bootstrap()
        .channel(this.transportType.datagramChannelClass)
        .group(this.workerGroup)
        .handler(new GS4QueryHandler(this.server))
        .localAddress(address);
    bootstrap.bind()
        .addListener((ChannelFutureListener) future -> {
          final Channel channel = future.channel();
          if (future.isSuccess()) {
            this.endpoints.put(address, channel);
            LOGGER.info("Listening for GS4 query on {}", channel.localAddress());
          } else {
            LOGGER.error("Can't bind to {}", bootstrap.config().localAddress(), future.cause());
          }
        });
  }

  public Bootstrap createWorker() {
    return this.createWorker(this.workerGroup);
  }

  /**
   * Creates a TCP {@link Bootstrap} using Velocity's event loops.
   *
   * @param group the event loop group to use
   *
   * @return a new {@link Bootstrap}
   */
  public Bootstrap createWorker(EventLoopGroup group) {
    return new Bootstrap()
        .channel(this.transportType.socketChannelClass)
        .group(group)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
            this.server.getConfiguration().getConnectTimeout())
        .resolver(this.resolverGroup);
  }

  /**
   * Closes the specified {@code oldBind} endpoint.
   *
   * @param oldBind the endpoint to close
   */
  public void close(InetSocketAddress oldBind) {
    Channel serverChannel = endpoints.remove(oldBind);
    Preconditions.checkState(serverChannel != null, "Endpoint %s not registered", oldBind);
    LOGGER.info("Closing endpoint {}", serverChannel.localAddress());
    serverChannel.close().syncUninterruptibly();
  }

  /**
   * Closes all endpoints.
   */
  public void shutdown() {
    for (final Channel endpoint : this.endpoints.values()) {
      try {
        LOGGER.info("Closing endpoint {}", endpoint.localAddress());
        endpoint.close().sync();
      } catch (final InterruptedException e) {
        LOGGER.info("Interrupted whilst closing endpoint", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  public EventLoopGroup getBossGroup() {
    return bossGroup;
  }

  public EventLoopGroup getWorkerGroup() {
    return workerGroup;
  }

  public ServerChannelInitializerHolder getServerChannelInitializer() {
    return this.serverChannelInitializer;
  }
}
