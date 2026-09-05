package io.github.spencerpark.jupyter.channels;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class JupyterInputStreamTest {

    @Test
    public void readReturnsUnsignedByteValues() throws Exception {
        JupyterInputStream input = new JupyterInputStream(StandardCharsets.UTF_8);
        Field dataField = JupyterInputStream.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(input, new byte[]{(byte) 0xFF, (byte) 0x80});

        Assert.assertEquals(255, input.read());
        Assert.assertEquals(128, input.read());
        Assert.assertEquals(-1, input.read());
    }
}
