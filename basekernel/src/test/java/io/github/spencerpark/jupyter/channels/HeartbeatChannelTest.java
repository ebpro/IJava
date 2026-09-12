package io.github.spencerpark.jupyter.channels;

import io.github.spencerpark.jupyter.kernel.KernelConnectionProperties;
import io.github.spencerpark.jupyter.messages.HMACGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicReference;

public class HeartbeatChannelTest {

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static AtomicReference<?> readPulse(HeartbeatChannel channel) throws Exception {
        Field field = HeartbeatChannel.class.getDeclaredField("pulse");
        field.setAccessible(true);
        return (AtomicReference<?>) field.get(channel);
    }

    @Test
    public void doubleBindIsRejected() throws Exception {
        ZMQ.Context ctx = ZMQ.context(1);
        int hbPort = findFreePort();
        KernelConnectionProperties props = new KernelConnectionProperties(
                "127.0.0.1", 0, 0, 0, hbPort, 0, "tcp", "hmac-sha256", "");
        HeartbeatChannel channel = new HeartbeatChannel(ctx, HMACGenerator.NO_AUTH_INSTANCE, 50);
        channel.bind(props);

        try {
            channel.bind(props);
            Assert.fail("Second bind should have been rejected");
        } catch (IllegalStateException e) {
            Assert.assertEquals("Heartbeat channel already bound", e.getMessage());
        }

        channel.close();
        channel.waitUntilClose();
        ctx.close();
    }

    @Test
    public void heartbeatEchoesPulse() throws Exception {
        ZMQ.Context ctx = ZMQ.context(1);
        int hbPort = findFreePort();
        KernelConnectionProperties props = new KernelConnectionProperties(
                "127.0.0.1", 0, 0, 0, hbPort, 0, "tcp", "hmac-sha256", "");
        HeartbeatChannel channel = new HeartbeatChannel(ctx, HMACGenerator.NO_AUTH_INSTANCE, 50);
        channel.bind(props);

        ZMQ.Socket client = ctx.socket(SocketType.REQ);
        client.setReceiveTimeOut(5000);
        client.connect("tcp://127.0.0.1:" + hbPort);
        client.send("ping");

        String echoed = client.recvStr();

        Assert.assertEquals("ping", echoed);

        client.close();
        channel.close();
        channel.waitUntilClose();
        ctx.close();
    }

    @Test
    public void pulseReferenceClearedAfterClose() throws Exception {
        ZMQ.Context ctx = ZMQ.context(1);
        int hbPort = findFreePort();
        KernelConnectionProperties props = new KernelConnectionProperties(
                "127.0.0.1", 0, 0, 0, hbPort, 0, "tcp", "hmac-sha256", "");
        HeartbeatChannel channel = new HeartbeatChannel(ctx, HMACGenerator.NO_AUTH_INSTANCE, 50);
        channel.bind(props);

        Assert.assertNotNull("Pulse reference should be set after bind", readPulse(channel).get());

        channel.close();
        channel.waitUntilClose();

        Assert.assertNull("Pulse reference should be cleared after close", readPulse(channel).get());

        ctx.close();
    }
}
