package github.veikkoroc.remote.transport.netty.client;

import github.veikkoroc.factory.SingletonFactory;
import github.veikkoroc.registry.ServiceDiscovery;
import github.veikkoroc.registry.zk_impl.ZkServiceDiscoveryImpl;
import github.veikkoroc.remote.entry.RpcRequest;
import github.veikkoroc.remote.entry.RpcResponse;
import github.veikkoroc.remote.transport.ClientTransport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 *
 *
 *  该类用于 在注册中心发现服务，获取与服务器通信的通道，发送 RpcRequest
 *
 *
 *
 * 根据Netty传输RpcRequest
 * @author Veikko Roc
 * @version 1.0
 * @date 2020/9/12 15:09
 */
@Slf4j
public class NettyClientTransport implements ClientTransport {

    /**
     * 发现服务
     * 获得InetSocketAddress
     */
    private final ServiceDiscovery serviceDiscovery;


    /**
     * 未处理完的消息
     */
    private final UnProcessedRequests unprocessedRequests;

    /**
     * 通道提供者
     */
    private final ChannelProvider channelProvider;

    public NettyClientTransport() {
        this.serviceDiscovery = new ZkServiceDiscoveryImpl();
        this.unprocessedRequests = SingletonFactory.getInstance(UnProcessedRequests.class);
        this.channelProvider = SingletonFactory.getInstance(ChannelProvider.class);
    }
    @Override
    public CompletableFuture<RpcResponse<Object>> sendRpcRequest(RpcRequest rpcRequest) {
        //创建返回值
        CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();
        //通过RPCRequest获得服务名
        String rpcServiceName = rpcRequest.toRpcServiceProperties().getRpcServicePropertiesFields();
        //获得服务地址
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcServiceName);
        //获取服务器地址相关通道
        Channel channel = channelProvider.getChannel(inetSocketAddress);
        log.info("==========客户端获取的通道[{}]",channel);
        log.info("==========客户端获取的通道是否活跃[{}]",channel.isActive());



        if (channel != null && channel.isActive()){
            //存放未处理的请求
            unprocessedRequests.put(rpcRequest.getRequestId(),resultFuture);
            log.info("==========正在处理的消息存放成功 [{}]",unprocessedRequests);
            log.info("==========需要发送的RPCRequest [{}]",rpcRequest);
            //发送消息到服务器
            //ChannelFuture的作用是用来保存Channel异步操作的结果。
            //添加ChannelFutureListener，以便于在I/O操作完成的时候，能够获得通知。
            channel.writeAndFlush(rpcRequest).addListener((ChannelFutureListener) future->{
                if (future.isSuccess()){
                    log.info("===========客户端发送消息：[{}]",rpcRequest);
                } else {
                    future.channel().close();
                    resultFuture.completeExceptionally(future.cause());
                    log.error("==========发送失败:[{}]",future.cause());
                }
            });

        }else {
            throw new IllegalArgumentException("==========在NettyClientTransport抛出异常");
        }

        return resultFuture;
    }
}
