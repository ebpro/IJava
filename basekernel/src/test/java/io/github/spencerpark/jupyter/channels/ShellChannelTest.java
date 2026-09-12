package io.github.spencerpark.jupyter.channels;

import io.github.spencerpark.jupyter.kernel.KernelConnectionProperties;
import io.github.spencerpark.jupyter.messages.HMACGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicReference;

public class ShellChannelTest {

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static AtomicReference<?> readIoloop(ShellChannel channel) throws Exception {
        Field field = ShellChannel.class.getDeclaredField("ioloop");
        field.setAccessible(true);
        return (AtomicReference<?>) field.get(channel);
    }

    @Test
    public void doubleBindIsRejected() throws Exception {
        KernelConnectionProperties props = new KernelConnectionProperties(
                "127.0.0.1", findFreePort(), findFreePort(), findFreePort(), findFreePort(), findFreePort(), "tcp", "hmac-sha256", "");
        JupyterConnection connection = new JupyterConnection(props);
        ZMQ.Context ctx = ZMQ.context(1);
        ShellChannel channel = new ShellChannel(ctx, HMACGenerator.NO_AUTH_INSTANCE, false, connection, 50);
        channel.bind(props);

        try {
            channel.bind(props);
            Assert.fail("Second bind should have been rejected");
        } catch (IllegalStateException e) {
            Assert.assertEquals("Shell channel already bound", e.getMessage());
        }

        channel.close();
        channel.waitUntilClose();
        connection.close();
        ctx.close();
    }

    @Test
    public void ioloopReferenceClearedAfterClose() throws Exception {
        KernelConnectionProperties props = new KernelConnectionProperties(
                "127.0.0.1", findFreePort(), findFreePort(), findFreePort(), findFreePort(), findFreePort(), "tcp", "hmac-sha256", "");
        JupyterConnection connection = new JupyterConnection(props);
        ZMQ.Context ctx = ZMQ.context(1);
        ShellChannel channel = new ShellChannel(ctx, HMACGenerator.NO_AUTH_INSTANCE, false, connection, 50);
        channel.bind(props);

        Assert.assertNotNull("Ioloop reference should be set after bind", readIoloop(channel).get());

        channel.close();
        channel.waitUntilClose();

        Assert.assertNull("Ioloop reference should be cleared after close", readIoloop(channel).get());

        connection.close();
        ctx.close();
    }
}
